package com.sense.organisation.service;

import java.io.ByteArrayOutputStream;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.DataFormatter;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.neo4j.ogm.session.Session;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sense.sensemodel.model.PropertyType;
import com.sense.sensemodel.model.organisation.OrgHierarchy;
import com.sense.sensemodel.model.organisation.Organisation;
import com.sense.sensemodel.repository.organisation.OrgHierarchyRepository;
import com.sense.sensemodel.repository.organisation.OrganisationRepository;

@Service
public class BulkUploadService {

	@Autowired
	Session session;
	
	@Autowired
	OrganisationRepository organisationRepository;

	@Autowired
	OrgHierarchyRepository orgHierarchyRepository;

	ObjectMapper objectMapper = new ObjectMapper();
	DataFormatter formatter = new DataFormatter();

	public ByteArrayOutputStream createSampleFile(String companyEntityId) throws Exception {
		Workbook workbook = new XSSFWorkbook();
		Sheet sheet = workbook.createSheet("OrgData");
		Row headerRow = sheet.createRow(0);
		int cellCounter = 0;
		OrgHierarchy current = orgHierarchyRepository.findByCompanyEntityIdAndType(companyEntityId, "company", -1);
		for (Set<OrgHierarchy> children; (children = current.getSubOrgTypes()) != null
				&& !current.getSubOrgTypes().isEmpty();) {
			// ASSUMING A FLAT ONE-ONE HIERARCHY FOR NOW
			current = children.iterator().next();
			Set<PropertyType> props = current.getAllowedProperties();
			headerRow.createCell(cellCounter++).setCellValue(current.getType() + "_no");
			headerRow.createCell(cellCounter++).setCellValue(current.getType() + "_name");
			for (PropertyType property : props) {
				headerRow.createCell(cellCounter++).setCellValue(property.getName());
			}
			headerRow.createCell(cellCounter++).setCellValue("#");
		}
		Cell lastCreatedCell = headerRow.getCell(cellCounter - 1);
		if (cellCounter != 0 && lastCreatedCell != null && lastCreatedCell.getStringCellValue().equals("#")) {
			headerRow.removeCell(lastCreatedCell);
		}
		for (int i = 0; i < cellCounter; i++) {
			sheet.autoSizeColumn(i);
		}
		ByteArrayOutputStream bos = new ByteArrayOutputStream();
		try {
			workbook.write(bos);
		} finally {
			bos.close();
		}
		workbook.close();
		return bos;
	}

	private void updatePropertiesAndHierarchy(Row columnNameRow, String companyEntityId) {
		OrgHierarchy companyType = orgHierarchyRepository.findByCompanyEntityIdAndType(companyEntityId, "company", -1);
		if (companyType == null) {
			companyType = orgHierarchyRepository.save(new OrgHierarchy(companyEntityId, "company", null));
		}
		List<OrgHierarchy> existingHierarchy = companyType.flattened().collect(Collectors.toList());
		OrgHierarchy parentHierarchy = companyType;
		Iterator<Cell> cellIterator = columnNameRow.iterator();
		while (cellIterator.hasNext()) {
			cellIterator.next();
			String entityType = cellIterator.next().getStringCellValue();
			OrgHierarchy entityTypeHierarchy = existingHierarchy.stream().filter(h -> h.getType().equals(entityType))
					.findFirst().orElse(null);
			// Check if hierarchy already exists and skip if it does
			if (entityTypeHierarchy == null) {
				entityTypeHierarchy = new OrgHierarchy(companyEntityId, entityType, parentHierarchy);
				entityTypeHierarchy.setAllowedProperties(new HashSet<>());
			}
			while (cellIterator.hasNext()) {
				String propName = cellIterator.next().getStringCellValue();
				if (propName.equals("#"))
					break;
				PropertyType newProperty = new PropertyType(propName, true, false);
				// Check if property type already exists and skip if it does
				if (!entityTypeHierarchy.getAllowedProperties().contains(newProperty)) {
					entityTypeHierarchy.getAllowedProperties().add(newProperty);
				}
			}
			entityTypeHierarchy = orgHierarchyRepository.save(entityTypeHierarchy);
			parentHierarchy = entityTypeHierarchy;
		}
	}

	public void bulkUpload(Organisation company, MultipartFile uploadfile) throws Exception {
		company = createEntityIfNotExists(company);
		try (Workbook workbook = new XSSFWorkbook(uploadfile.getInputStream())) {
			Sheet datatypeSheet = workbook.getSheetAt(0);
			Iterator<Row> rowIterator = datatypeSheet.iterator();
			if (!rowIterator.hasNext()) {
				throw (new RuntimeException("blank sheet"));
			}
			Row columnNameRow = rowIterator.next();
			updatePropertiesAndHierarchy(columnNameRow, company.getEntityId());

			while (rowIterator.hasNext()) {
				Row currentRow = rowIterator.next();
				Iterator<Cell> cellIterator = currentRow.iterator();
				createNextEntityInCurrentRow(company, cellIterator, company.getEntityId());
				// Clearing session after each row so that session does not track parent
				// and delete relationships with last child after creation of next
				session.clear();
			}
		}
	}

	private void createNextEntityInCurrentRow(Organisation parent, Iterator<Cell> cellIterator,
			String companyEntityId) {
		if (!cellIterator.hasNext()) {
			return;
		}
		Cell entityIdCell = cellIterator.next();
		String entityId = formatter.formatCellValue(entityIdCell);
		Cell entityNameCell = cellIterator.next();
		String entityName = formatter.formatCellValue(entityNameCell);
		String entityType = entityNameCell.getSheet().getRow(0).getCell(entityNameCell.getColumnIndex())
				.getRichStringCellValue().toString();
		entityName = entityName.isEmpty() ? entityType : entityName;

		Map<String, String> propertiesMap = new HashMap<>();
		while (cellIterator.hasNext()) {
			Cell propCell = cellIterator.next();
			String propValue = formatter.formatCellValue(propCell);
			if (propValue.equals("#"))
				break;
			String propName = propCell.getSheet().getRow(0).getCell(propCell.getColumnIndex()).getRichStringCellValue()
					.toString();
			propertiesMap.put(propName, propValue);
		}
		Organisation entity = new Organisation(entityName, entityType, entityId, propertiesMap, true);
		entity.setParent(parent);
		// Checking db and not in memory to support rerun if fails in the middle
		entity = createEntityIfNotExists(entity);
		createNextEntityInCurrentRow(entity, cellIterator, companyEntityId);
	}

	private Organisation createEntityIfNotExists(Organisation entity) {
		Organisation foundEntity = organisationRepository.findByEntityIdAndActiveInd(entity.getEntityId(), true)
				.orElse(null);
		if (foundEntity == null) {
			entity.setActiveInd(true);
			entity = organisationRepository.save(entity);
			return entity;
		} else {
			return foundEntity;
		}
	}

	public void bulkUpdate(String companyEntityId, MultipartFile uploadfile) throws Exception {
		Organisation company = organisationRepository.findByEntityIdAndActiveInd(companyEntityId, true)
				.orElseThrow(() -> new RuntimeException("no active company found with entityId: " + companyEntityId));
		try (Workbook workbook = new XSSFWorkbook(uploadfile.getInputStream())) {
			Sheet datatypeSheet = workbook.getSheetAt(0);
			Iterator<Row> rowIterator = datatypeSheet.iterator();
			if (!rowIterator.hasNext()) {
				throw (new RuntimeException("blank sheet"));
			}
			Row columnNamesRow = rowIterator.next();

			OrgHierarchy companyType = orgHierarchyRepository.findByCompanyEntityIdAndType(companyEntityId, "company",
					-1);
			List<OrgHierarchy> existingHierarchy = companyType.flattened().collect(Collectors.toList());

			while (rowIterator.hasNext()) {
				Row currentRow = rowIterator.next();
				Iterator<Cell> cellIterator = currentRow.iterator();
				updateEntityInCurrentRow(cellIterator, existingHierarchy);
			}
			orgHierarchyRepository.saveAll(existingHierarchy);
		}
	}

	private void updateEntityInCurrentRow(Iterator<Cell> cellIterator, List<OrgHierarchy> existingHierarchy)
			throws Exception {
		if (!cellIterator.hasNext()) {
			return;
		}
		Cell entityIdCell = cellIterator.next();
		String entityId = formatter.formatCellValue(entityIdCell);
		Cell entityNameCell = cellIterator.next();
		Organisation entity = organisationRepository.findByEntityIdAndActiveInd(entityId, true)
				.orElseThrow(() -> new RuntimeException("no active entity found with entityId: " + entityId));

		String propName = cellIterator.next().getStringCellValue();
		String propValue = formatter.formatCellValue(cellIterator.next());
		entity.getProperties().put(propName, propValue);
		organisationRepository.save(entity);
		OrgHierarchy entityTypeHierarchy = existingHierarchy.stream().filter(h -> h.getType().equals(entity.getType()))
				.findFirst()
				.orElseThrow(() -> new RuntimeException("OrgHierarchy not found found for: " + entity.getEntityId()));
		if (!entityTypeHierarchy.getAllowedProperties().stream().anyMatch(p -> p.getName().equals(propName))) {
			entityTypeHierarchy.getAllowedProperties().add(new PropertyType(propName, true, false));
		}
	}
}
