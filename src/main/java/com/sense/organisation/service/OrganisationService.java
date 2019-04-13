package com.sense.organisation.service;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import com.sense.sensemodel.model.assets.Asset;
import com.sense.sensemodel.model.assets.AssetConnection;
import com.sense.sensemodel.model.organisation.OrgHierarchy;
import com.sense.sensemodel.model.organisation.Organisation;
import com.sense.sensemodel.repository.assets.AssetConnectionRepository;
import com.sense.sensemodel.repository.assets.AssetRepository;
import com.sense.sensemodel.repository.organisation.OrgHierarchyRepository;
import com.sense.sensemodel.repository.organisation.OrganisationRepository;

@Service
public class OrganisationService {
	@Autowired
	private OrganisationRepository organisationRepository;

	@Autowired
	private OrgHierarchyRepository orgHierarchyRepository;

	@Autowired
	private AssetRepository assetRepository;

	@Autowired
	private AssetConnectionRepository assetConnectionRepository;

	private Logger logger = LoggerFactory.getLogger(OrganisationService.class);

	public Set<Organisation> getChildrenForEntity(String entityId) {
		Organisation parent = organisationRepository.findByEntityIdAndActiveInd(entityId, true)
				.orElseThrow(() -> new RuntimeException("no active entity found with entityId: " + entityId));
		return parent.getActiveChildren();
	}

	public void addOrgToParent(Organisation organisation, String parentId, String companyEntityId) {
		if (organisationRepository.findByEntityIdAndActiveInd(organisation.getEntityId(), true).isPresent()) {
			throw new RuntimeException("An active entity already exists with entityId: " + organisation.getEntityId());
		}
		organisation.setActiveInd(true);
		// To create a company leave parent as null
		if (parentId != null && !parentId.equals(""))

		{
			OrgHierarchy orgHierarchy = orgHierarchyRepository.findByCompanyEntityIdAndType(companyEntityId,
					organisation.getType(), 0);
			if (orgHierarchy == null)
				throw new RuntimeException(
						"given type: " + organisation.getType() + " not allowed for company: " + companyEntityId);
			Organisation parent = organisationRepository.findByEntityIdAndActiveInd(parentId, true)
					.orElseThrow(() -> new RuntimeException("No active parent found with entityId: " + parentId));
			organisation.setParent(parent);
		}
		if ((parentId == null || parentId.equals("")) && !organisation.getType().equals("company"))
			throw new RuntimeException("invalid params for entityId: " + organisation.getEntityId());

		organisationRepository.save(organisation);
		logger.info("created org: " + organisation.getName() + " under parent: " + parentId);
		
	}

	// TODO: MAKE TRASACTIONAL
	// TODO: CALL ASSET MICROSERVICE
	public void deleteOrg(String entityId) {
		List<Organisation> entities = new ArrayList<>();
		entities.add(organisationRepository.findByEntityIdAndActiveInd(entityId, true)
				.orElseThrow(() -> new RuntimeException("No active entity found with entityId: " + entityId)));
		deleteHierarchy(entities);
		List<String> descendantAssetsCodes = assetRepository.getAllDescendantAssetsCodesForOrg(entityId);
		List<Asset> descendantAssets = assetRepository.findByCodeInAndActiveInd(descendantAssetsCodes, true);
		descendantAssets.stream().forEach(a -> {
			a.setActiveInd(false);
			a.setDeactivationTime(new Date());
		});
		assetRepository.save(descendantAssets, 0);
		logger.info("deleted org: " + entityId);
	}

	private void deleteHierarchy(List<Organisation> entities) {
		for (Organisation entity : entities) {
			entity.setActiveInd(false);
			Set<Organisation> activeChildren = entity.getActiveChildren();
			activeChildren.stream().forEach(e -> e.setActiveInd(false));
			organisationRepository.save(entity);
			organisationRepository.saveAll(activeChildren);
		}
	}

	// TODO: call asset micro service to create connection
	private void createAssetConnection(String sourceAssetId, String destinationAssetId, String connectionType,
			Map<String, String> properties) {
		Asset sourceAsset = assetRepository.findByCodeAndActiveInd(sourceAssetId, true)
				.orElseThrow(() -> new RuntimeException("No active asset found for id: " + sourceAssetId));
		Asset destinationAsset = assetRepository.findByCodeAndActiveInd(destinationAssetId, true)
				.orElseThrow(() -> new RuntimeException("No active asset found for id: " + destinationAssetId));
		if (sourceAsset.getConnnectionOut().stream()
				.anyMatch(co -> co.getEndAsset().getId().equals(destinationAsset.getId()))) {
			logger.info("An existing connection of given type: " + connectionType + " already exists between aseets: "
					+ sourceAssetId + " and " + destinationAssetId + " Skipping...");
			return;
		}
		AssetConnection connection = new AssetConnection(sourceAsset, destinationAsset, connectionType, properties);
		assetConnectionRepository.save(connection, 0);
	}

	public void reassignEntity(String entityId, String newParentId) {
		Organisation newParent = organisationRepository.findByEntityIdAndActiveInd(newParentId, true)
				.orElseThrow(() -> new RuntimeException("No active parent found with entityId: " + newParentId));

		Organisation existingEntity = organisationRepository.findByEntityIdAndActiveInd(entityId, true)
				.orElseThrow(() -> new RuntimeException("No active entity found with entityId: " + entityId));
		List<Asset> oldAssetsToConnect = new LinkedList<>();
		reAssignHierarchy(newParent, existingEntity, oldAssetsToConnect);
		for (Asset oldAsset : oldAssetsToConnect) {
			for (AssetConnection conOut : oldAsset.getConnnectionOut()) {
				createAssetConnection(conOut.getStartAsset().getCode(), conOut.getEndAsset().getCode(),
						conOut.getType(), conOut.getProperties());
			}
			for (AssetConnection conIn : oldAsset.getConnnectionIn()) {
				createAssetConnection(conIn.getStartAsset().getCode(), conIn.getEndAsset().getCode(), conIn.getType(),
						conIn.getProperties());
			}
		}
	}

	// To maintain history of leaf node data related to a hierarchy
	// TODO: MAKE TRANSACTIONAL
	private void reAssignHierarchy(Organisation newParent, Organisation existingEntity,
			List<Asset> oldAssetsToConnect) {
		Organisation newEntity = new Organisation(existingEntity.getName(), existingEntity.getType(),
				existingEntity.getEntityId(), existingEntity.getProperties(), true);
		newEntity.setParent(newParent);
		existingEntity.setActiveInd(false);
		organisationRepository.saveAll(Arrays.asList(existingEntity, newEntity));

		List<String> childAssetsCodes = assetRepository.getAllChildrenAssetsCodesForOrg(existingEntity.getId());
		List<Asset> childAssets = assetRepository.findByCodeInAndActiveInd(childAssetsCodes, true);
		List<Asset> assetsTosave = new ArrayList<>();
		for (Asset oldAsset : childAssets) {
			oldAsset.setActiveInd(false);
			oldAsset.setDeactivationTime(new Date());
			oldAssetsToConnect.add(oldAsset);
			Asset newAsset = new Asset(oldAsset.getName(), oldAsset.getType(), oldAsset.getCode(), newEntity,
					oldAsset.getProperties(), true);
			assetsTosave.add(oldAsset);
			assetsTosave.add(newAsset);
		}
		assetRepository.saveAll(assetsTosave);
		Set<Organisation> children = existingEntity.getActiveChildren();
		for (Organisation child : children) {
			reAssignHierarchy(newEntity, child, oldAssetsToConnect);
		}
	}

	public List<OrgHierarchy> getOrgSubTypesForCompany(String companyEntityId) {
		return orgHierarchyRepository.findByCompanyEntityId(companyEntityId, 0);
	}

	public List<Organisation> getOrgPartsForCompany(String companyEntityId, String type) {
		return organisationRepository.findActiveSubOrgsByType(type, companyEntityId, 0);
	}
}
