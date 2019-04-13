package com.sense.organisation;


import org.hamcrest.Matchers;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.test.context.junit4.SpringJUnit4ClassRunner;
import org.springframework.test.context.junit4.SpringRunner;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.RequestBuilder;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.xmlunit.input.WhitespaceNormalizedSource;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sense.organisation.controller.OrganisationController;
import com.sense.organisation.service.OrganisationService;
import com.sense.sensemodel.model.organisation.OrgHierarchy;
import com.sense.sensemodel.model.organisation.Organisation;

import static org.junit.Assert.assertEquals;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.doThrow;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

@RunWith(SpringRunner.class)
public class TestOrganisationController {

	
	private MockMvc mockMvc;
	
	@Mock
	OrganisationService organisationService;
	
	@InjectMocks
	OrganisationController organisationController;
	
	@Before
	public void setUp() {
		mockMvc = MockMvcBuilders.standaloneSetup(organisationController).build();
	}
	
	@Test
	public void testGetChildrenForEntity() throws Exception{
		Set<Organisation> parrent = new HashSet<Organisation>();
		
		Organisation child1 = new Organisation();
		child1.setActiveInd(true);
		child1.setEntityId("cr001");
		child1.setName("cr1");
		child1.setType("circle");
		
		Organisation child2 = new Organisation();
		child2.setActiveInd(true);
		child2.setEntityId("cr002");
		child2.setName("cr2");
		child2.setType("circle");
	
		parrent.add(child1);
		parrent.add(child2);
		
		Mockito.when(organisationService.getChildrenForEntity(Mockito.anyString())).thenReturn(parrent);
		
		String URI = "/org/children/c001";
		
		RequestBuilder requestBuilder = MockMvcRequestBuilders.get(URI).accept(MediaType.APPLICATION_JSON);
		MvcResult result = mockMvc.perform(requestBuilder).andReturn();
		String expectedJson = this.mapToJson(parrent);
		String outputInJson = result.getResponse().getContentAsString();
		assertEquals(HttpStatus.OK.value(),result.getResponse().getStatus());
		assertEquals(expectedJson, outputInJson);
	}
	
	@Test
	public void testAddChildToParent() throws Exception {
		Organisation company = new Organisation();
		company.setId(1l);
		company.setName("d1");
		company.setEntityId("d001");
		company.setType("division");
		company.setActiveInd(true);
		
		String inputInJson = this.mapToJson(company);
		Mockito.doNothing().when(organisationService)
										.addOrgToParent(Mockito.any(Organisation.class), Mockito.anyString(), Mockito.anyString());
		
		String URI = "/org/add";
		RequestBuilder requestBuilder = MockMvcRequestBuilders
				.put(URI)
				.param("parent", "c001")
				.param("companyEntityId", "BRPL")
				.accept(MediaType.APPLICATION_JSON).content(inputInJson)
				.contentType(MediaType.APPLICATION_JSON);
		MvcResult result = mockMvc.perform(requestBuilder).andReturn();
		MockHttpServletResponse response = result.getResponse();
		assertEquals(HttpStatus.OK.value(),response.getStatus());
		
	}
	
	@Test
	public void testGetOrgSubTypesForCompany() throws Exception {
		List<OrgHierarchy> company = new ArrayList<OrgHierarchy>();
		OrgHierarchy orgSubType1 = new OrgHierarchy();
		orgSubType1.setCompanyEntityId("BRPL");
		orgSubType1.setType("divisoin");
		
		OrgHierarchy orgSubType2 = new OrgHierarchy();
		orgSubType2.setCompanyEntityId("BRPL");
		orgSubType2.setType("zone");
		
		company.add(orgSubType1);
		company.add(orgSubType2);
		
		String URI = "/org/getAllSubTypes/BRPL";
		
		Mockito.when(organisationService.getOrgSubTypesForCompany(Mockito.anyString())).thenReturn(company);
		
		RequestBuilder requestBuilder = MockMvcRequestBuilders.get(URI).accept(MediaType.APPLICATION_JSON);
		MvcResult result = mockMvc.perform(requestBuilder).andReturn();
		String expectedJson = this.mapToJson(company);
		String outputInJson = result.getResponse().getContentAsString();
		assertEquals(HttpStatus.OK.value(),result.getResponse().getStatus());
		assertEquals(expectedJson, outputInJson);	
	}
	
	@Test
	public void testGetOrgPartsOfType() throws Exception {
		List<Organisation> orgPartForCompany = new ArrayList<Organisation>();
		Organisation orgPart1 = new Organisation();
		orgPart1.setId(1l);
		orgPart1.setEntityId("z001");
		orgPart1.setName("z1");
		orgPart1.setType("zone");
		orgPart1.setActiveInd(true);
		
		Organisation orgPart2 = new Organisation();
		orgPart2.setId(2l);
		orgPart2.setEntityId("z002");
		orgPart2.setName("z2");
		orgPart2.setType("zone");
		orgPart2.setActiveInd(true);
		
		orgPartForCompany.add(orgPart1);
		orgPartForCompany.add(orgPart2);
		
		Mockito.when(organisationService.getOrgPartsForCompany(Mockito.anyString(), Mockito.anyString())).thenReturn(orgPartForCompany);
		
		String URI = "/org/getOrgPartsOfType/c001/zone";
		
		RequestBuilder requestBuilder = MockMvcRequestBuilders.get(URI).accept(MediaType.APPLICATION_JSON);
		MvcResult result = mockMvc.perform(requestBuilder).andReturn();
		String expectedJson = this.mapToJson(orgPartForCompany);
		String outputJson = result.getResponse().getContentAsString();
		assertEquals(HttpStatus.OK.value(),result.getResponse().getStatus());
		assertEquals(expectedJson, outputJson);
		
	}
	
	@Test
	public void delete() throws Exception{
		Mockito.doNothing().when(organisationService).deleteOrg(Mockito.anyString());
		
		String URI = "/org/delete/z001";
		RequestBuilder requestBuilder = MockMvcRequestBuilders.delete(URI)
				.accept(MediaType.APPLICATION_JSON)
				.contentType(MediaType.APPLICATION_JSON);
		MvcResult result = mockMvc.perform(requestBuilder).andReturn();
		assertEquals(HttpStatus.OK.value(), result.getResponse().getStatus());
			
	}
	
	@Test
	public void reassign() throws Exception{
		Mockito.doNothing().when(organisationService).reassignEntity(Mockito.anyString(),Mockito.anyString());
		
		String URI = "/org/reassign";
		RequestBuilder requestBuilder = MockMvcRequestBuilders
				.post(URI)
				.param("newParent", "d001")
				.param("entity", "z001")
				.contentType(MediaType.APPLICATION_JSON)
				.accept(MediaType.APPLICATION_JSON);
				
		MvcResult result = mockMvc.perform(requestBuilder).andReturn();
		assertEquals(HttpStatus.OK.value(), result.getResponse().getStatus());
			
	}
	
	public String mapToJson(Object object) throws JsonProcessingException {
		ObjectMapper objectMapper = new ObjectMapper();
		return objectMapper.writeValueAsString(object);
		
	}

}
