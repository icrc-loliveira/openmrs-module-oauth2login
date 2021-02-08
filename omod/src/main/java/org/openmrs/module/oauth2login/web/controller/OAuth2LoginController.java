/*
 * This Source Code Form is subject to the terms of the Mozilla Public License,
 * v. 2.0. If a copy of the MPL was not distributed with this file, You can
 * obtain one at http://mozilla.org/MPL/2.0/. OpenMRS is also distributed under
 * the terms of the Healthcare Disclaimer located at http://openmrs.org/license.
 *
 * Copyright (C) OpenMRS Inc. OpenMRS is a registered trademark and the OpenMRS
 * graphic logo is a trademark of OpenMRS Inc.
 */
package org.openmrs.module.oauth2login.web.controller;

import static org.springframework.web.bind.annotation.RequestMethod.GET;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.Collection;
import java.util.Properties;

import org.apache.commons.lang3.StringUtils;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.openmrs.api.context.Context;
import org.openmrs.api.context.ContextAuthenticationException;
import org.openmrs.module.oauth2login.authscheme.OAuth2TokenCredentials;
import org.openmrs.module.oauth2login.authscheme.UserInfo;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.client.RestOperations;
import org.springframework.web.servlet.ModelAndView;

@Controller
public class OAuth2LoginController {
	
	protected final Log log = LogFactory.getLog(getClass());
	
	private String userInfoUri;
	
	private Properties oauth2Props;
	
	private RestOperations restTemplate; // to fetch user infos from OAuth2 provider
	
	private AuthenticationPostProcessor postProcessor;
	
	@Autowired
	public void setRestTemplate(@Qualifier("oauth2.restTemplate") RestOperations restTemplate) {
		this.restTemplate = restTemplate;
	}
	
	@Autowired
	public void setOAuth2Properties(@Qualifier("oauth2.properties") Properties oauth2Props) {
		this.oauth2Props = oauth2Props;
	}
	
	@Autowired
	public void setUserInfoUri(@Qualifier("oauth2.userInfoUri") String userInfoUri) {
		this.userInfoUri = userInfoUri;
	}
	
	@Autowired
	public void setPostProcessor(@Qualifier("oauth2.postProcessor") AuthenticationPostProcessor postProcessor) {
		this.postProcessor = postProcessor;
	}
	
	@RequestMapping(value = "/oauth2login", method = GET)
	public ModelAndView login() {
		
		authenticateWithSpringSecurity();
		
		String userInfoJson = "{}";
		try {
			userInfoJson = restTemplate.getForObject(new URI(userInfoUri), String.class);
		}
		catch (URISyntaxException e) {
			throw new RuntimeException(e);
		}
		
		final UserInfo userInfo = new UserInfo(oauth2Props, userInfoJson);
		try {
			Context.authenticate(new OAuth2TokenCredentials(userInfo));
		}
		catch (ContextAuthenticationException e) {
			throw new RuntimeException("The user '" + userInfo + "' could not be authenticated with the identity provider.",
			        e);
		}
		finally {
			log.info("The user '" + userInfo + "' was successfully authenticated with the identity provider.");
		}
		
		if (postProcessor != null) {
			postProcessor.process(userInfo);
		}
		
		return new ModelAndView("redirect:" + getRedirectUri());
	}
	
	private String getRedirectUri() {
		final String gpRedirectUri = "oauth2login.redirectUriAfterLogin";
		
		String redirectUri = Context.getAdministrationService().getGlobalProperty(gpRedirectUri);
		final String defaultRedirectUri = "/";
		if (StringUtils.isEmpty(redirectUri)) {
			log.info("Redirecting user to the default URI '" + defaultRedirectUri
			        + "'. This can be changed through the global property '" + gpRedirectUri + "'.");
		} else {
			log.info("Redirecting user to '" + redirectUri + "' as defined by the global property '" + gpRedirectUri + "'.");
		}
		
		return StringUtils.defaultIfBlank(redirectUri, defaultRedirectUri);
	}
	
	private void authenticateWithSpringSecurity() {
		SecurityContextHolder.getContext().setAuthentication(new CustomAuthentication());
	}
	
	/**
	 * Custom authentication used to have the token used by {@link CustomLogoutSuccessHandler} if
	 * needed
	 */
	public static class CustomAuthentication implements Authentication {
		
		@Override
		public String getName() {
			// TODO Check what should be appropriate here
			return "internal";
		}
		
		@Override
		public void setAuthenticated(boolean isAuthenticated) throws IllegalArgumentException {
		}
		
		@Override
		public boolean isAuthenticated() {
			return true;
		}
		
		@Override
		public Object getPrincipal() {
			// TODO Check what should be appropriate here
			return "internal";
		}
		
		/**
		 * @return the token if present
		 */
		@Override
		public Object getDetails() {
			return null;
		}
		
		@Override
		public Object getCredentials() {
			return null;
		}
		
		@Override
		public Collection<? extends GrantedAuthority> getAuthorities() {
			return null;
		}
	}
}
