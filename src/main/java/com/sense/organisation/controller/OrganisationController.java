package com.sense.organisation.controller;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.bind.annotation.RestController;

import com.sense.organisation.service.OrganisationService;
import com.sense.sensemodel.model.organisation.Organisation;

@RestController
@RequestMapping("/org")
public class OrganisationController {

	@Autowired
	private OrganisationService organisationService;

	private Logger logger = LoggerFactory.getLogger(OrganisationController.class);

	@RequestMapping(value = "/children/{entityId}", method = RequestMethod.GET)
	@ResponseBody
	ResponseEntity<?> getChildrenForEntity(@PathVariable("entityId") String entityId) {
		try {
			return new ResponseEntity<>(organisationService.getChildrenForEntity(entityId), HttpStatus.OK);
		} catch (Exception e) {
			logger.error("error getting children for entityId" + entityId, e);
			return new ResponseEntity<>(HttpStatus.INTERNAL_SERVER_ERROR);
		}
	}

	@RequestMapping(value = "/getAllSubTypes/{companyEntityId}", method = RequestMethod.GET)
	@ResponseBody
	ResponseEntity<?> getOrgSubTypesForCompany(@PathVariable("companyEntityId") String companyEntityId) {
		try {
			return new ResponseEntity<>(organisationService.getOrgSubTypesForCompany(companyEntityId), HttpStatus.OK);
		} catch (Exception e) {
			logger.error("error getting types for company: " + companyEntityId, e);
			return new ResponseEntity<>(HttpStatus.INTERNAL_SERVER_ERROR);
		}
	}

	@RequestMapping(value = "/getOrgPartsOfType/{companyEntityId}/{type}", method = RequestMethod.GET)
	@ResponseBody
	ResponseEntity<?> getOrgPartsForCompany(@PathVariable("companyEntityId") String companyEntityId,
			@PathVariable("type") String type) {
		try {
			return new ResponseEntity<>(organisationService.getOrgPartsForCompany(companyEntityId, type),
					HttpStatus.OK);
		} catch (Exception e) {
			logger.error("error getting parts for company: " + companyEntityId, e);
			return new ResponseEntity<>(HttpStatus.INTERNAL_SERVER_ERROR);
		}
	}

	@RequestMapping(value = "/add", method = RequestMethod.PUT)
	ResponseEntity<String> add(@RequestParam(required = false, name = "parent") String parentId,
			@RequestParam(required = false, name = "companyEntityId") String companyEntityId,
			@RequestBody Organisation organisation) {
		try {
			organisationService.addOrgToParent(organisation, parentId, companyEntityId);
			return new ResponseEntity<>("created", HttpStatus.OK);
		} catch (Exception e) {
			logger.error("Error creating org", e);
			return new ResponseEntity<>(HttpStatus.INTERNAL_SERVER_ERROR);
		}
	}

	@RequestMapping(value = "/delete/{entityId}", method = RequestMethod.DELETE)
	ResponseEntity<String> delete(@PathVariable("entityId") String entityId) {
		try {
			organisationService.deleteOrg(entityId);
			return new ResponseEntity<>("deleted", HttpStatus.OK);
		} catch (Exception e) {
			logger.error("Error deleting org", e);
			return new ResponseEntity<>(HttpStatus.INTERNAL_SERVER_ERROR);
		}
	}

	@RequestMapping(value = "/reassign", method = RequestMethod.POST)
	ResponseEntity<String> reassign(@RequestParam("newParent") String newParentId,
			@RequestParam("entity") String entityId) {
		try {
			organisationService.reassignEntity(entityId, newParentId);
			return new ResponseEntity<>("reassigned", HttpStatus.OK);
		} catch (Exception e) {
			logger.error("Error reassigning org", e);
			return new ResponseEntity<>(HttpStatus.INTERNAL_SERVER_ERROR);
		}
	}

}
