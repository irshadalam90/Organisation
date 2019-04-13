package com.sense.organisation.controller;

import java.io.ByteArrayOutputStream;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import com.sense.organisation.service.BulkUploadService;
import com.sense.sensemodel.model.organisation.Organisation;

@RestController
@RequestMapping("/org/bulk")
public class BulkUploadContoller {

	@Autowired
	private BulkUploadService bulkUploadService;

	private Logger logger = LoggerFactory.getLogger(BulkUploadContoller.class);

	@RequestMapping(value = "/upload", method = RequestMethod.PUT)
	ResponseEntity<?> bulkUpload(@RequestParam String companyEntityId, @RequestParam String companyName,
			@RequestParam(value = "file") MultipartFile uploadfile) {
		try {
			bulkUploadService.bulkUpload(new Organisation(companyName, "company", companyEntityId, null, true),
					uploadfile);
			return new ResponseEntity<>("uploaded", HttpStatus.OK);
		} catch (Exception e) {
			logger.error("Error creating org", e);
			return new ResponseEntity<>(HttpStatus.INTERNAL_SERVER_ERROR);
		}
	}

	@RequestMapping(value = "/update", method = RequestMethod.POST)
	ResponseEntity<String> bulkAddUpdateProperties(@RequestParam String companyEntityId,
			@RequestParam(value = "file") MultipartFile uploadfile) {
		try {
			bulkUploadService.bulkUpdate(companyEntityId, uploadfile);
			return new ResponseEntity<>("updated", HttpStatus.OK);
		} catch (Exception e) {
			logger.error("Error updating orgs", e);
			return new ResponseEntity<>(HttpStatus.INTERNAL_SERVER_ERROR);
		}
	}

	@RequestMapping(value = "/getSample", method = RequestMethod.GET)
	ResponseEntity<?> getSampleFile(@RequestParam String companyEntityId) {
		try {
			ByteArrayOutputStream fileStream = bulkUploadService.createSampleFile(companyEntityId);
			HttpHeaders headers = new HttpHeaders();
			// headers.setContentType(MediaType.parseMediaType("application/pdf"));
			String filename = "sample.xlsx";
			headers.setContentDispositionFormData(filename, filename);
			headers.setCacheControl("must-revalidate, post-check=0, pre-check=0");
			ResponseEntity<byte[]> response = new ResponseEntity<>(fileStream.toByteArray(), headers, HttpStatus.OK);
			return response;
		} catch (Exception e) {
			logger.error("Error getting sample", e);
			return new ResponseEntity<>(HttpStatus.INTERNAL_SERVER_ERROR);
		}
	}

}
