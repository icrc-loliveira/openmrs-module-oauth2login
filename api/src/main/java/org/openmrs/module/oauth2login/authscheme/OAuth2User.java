/*
 * This Source Code Form is subject to the terms of the Mozilla Public License,
 * v. 2.0. If a copy of the MPL was not distributed with this file, You can
 * obtain one at http://mozilla.org/MPL/2.0/. OpenMRS is also distributed under
 * the terms of the Healthcare Disclaimer located at http://openmrs.org/license.
 *
 * Copyright (C) OpenMRS Inc. OpenMRS is a registered trademark and the OpenMRS
 * graphic logo is a trademark of OpenMRS Inc.
 */
package org.openmrs.module.oauth2login.authscheme;

import java.util.List;
import java.util.Properties;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.apache.commons.lang3.StringUtils;
import org.openmrs.Person;
import org.openmrs.PersonName;
import org.openmrs.User;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.jayway.jsonpath.JsonPath;

public class OAuth2User {
	
	private final static Logger log = LoggerFactory.getLogger(OAuth2User.class);
	
	public final static String MAPPINGS_PFX = "openmrs.mapping.";
	
	public final static String PROP_USERNAME = "user.username";
	
	public final static String PROP_EMAIL = "user.email";
	
	public final static String PROP_GIVEN_NAME = "person.givenName";
	
	public final static String PROP_MIDDLE_NAME = "person.middleName";
	
	public final static String PROP_FAMILY_NAME = "person.familyName";
	
	public final static String PROP_GENDER = "person.gender";
	
	public final static String PROP_SYSTEMID = "user.systemId";
	
	public static final String PROP_ROLES = "user.roles";
	
	private Properties properties;
	
	private String userInfoJson; // user info as obtained from the OAuth 2 provider
	
	/**
	 * @param properties The Properties for mapping the Oauth 2 provider.
	 * @param userInfoJson The OAuth 2 user info response JSON.
	 */
	public OAuth2User(String userInfoJson, Properties properties) {
		this.userInfoJson = userInfoJson;
		this.properties = properties;
	}
	
	@Override
	public String toString() {
		return getOpenmrsUsername();
	}
	
	public String getOpenmrsUsername() {
		return OAuth2User.get(userInfoJson, OAuth2User.MAPPINGS_PFX + OAuth2User.PROP_USERNAME, properties);
	}
	
	/**
	 * Converts the OAUth user info into an OpenMRS user based on the OAuth 2 properties mappings.
	 * 
	 * @return The OpenMRS {@link User}
	 */
	public User toOpenmrsUser() {
		
		User user = new User();
		user.setUsername(getOpenmrsUsername());
		user.setSystemId(get(userInfoJson, MAPPINGS_PFX + PROP_SYSTEMID, properties));
		user.setEmail(get(userInfoJson, MAPPINGS_PFX + PROP_EMAIL, properties, null));
		
		Person person = new Person();
		person.setGender(get(userInfoJson, MAPPINGS_PFX + PROP_GENDER, properties, "n/a"));
		PersonName name = new PersonName();
		name.setGivenName(get(userInfoJson, MAPPINGS_PFX + PROP_GIVEN_NAME, properties));
		name.setMiddleName(get(userInfoJson, MAPPINGS_PFX + PROP_MIDDLE_NAME, properties));
		name.setFamilyName(get(userInfoJson, MAPPINGS_PFX + PROP_FAMILY_NAME, properties));
		
		user.setPerson(person);
		user.addName(name);
		
		return user;
	}
	
	/**
	 * Extracts values from the user info JSON based on a one to one mapping defined via the OAuth 2
	 * properties file.
	 * 
	 * @param userInfoJson The OAuth 2 user info response JSON.
	 * @param propertyKey The property key to look for, eg "openmrs.mapping.user.username".
	 * @param props The mappings between the user info fields and the corresponding OpenMRS
	 *            user/person properties.
	 * @param defaultValue The value to default to if no property could be found.
	 * @return The corresponding value from the JSON, an empty String if none is found.
	 */
	public static String get(String userInfoJson, String propertyKey, Properties props, String defaultValue) {
		String propertyValue = props.getProperty(propertyKey, null);
		String res = defaultValue;
		if (!StringUtils.isEmpty(propertyValue)) {
			res = JsonPath.read(userInfoJson, "$." + propertyValue);
		} else {
			log.warn("There was an attempt to read the value of " + propertyKey
			        + " out of the user info JSON, but the JSON property could not be found.");
		}
		return res;
	}
	
	/**
	 * Convenience method that retrieves the list of role names from the corresponding custom OAuth
	 * 2 property CSV value.
	 * 
	 * @return The list of role names, eg. ["Nurse", "Anaesthesia Assistant"].
	 */
	
	public List<String> getRoleNames() {
		String roleNames = get(userInfoJson, MAPPINGS_PFX + PROP_ROLES, properties, "");
		return Stream.of(roleNames.split(","))
				.filter(n -> StringUtils.isNoneBlank(n))
				.map(n -> StringUtils.trim(n))
				.collect(Collectors.toList());
	}
	
	/**
	 * @see #get(String, String, Properties, String)
	 */
	public static String get(String userInfoJson, String propertyKey, Properties props) {
		return get(userInfoJson, propertyKey, props, "");
	}
	
}
