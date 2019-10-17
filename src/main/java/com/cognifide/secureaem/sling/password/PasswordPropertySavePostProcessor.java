package com.cognifide.secureaem.sling.password;

import com.adobe.granite.crypto.CryptoException;
import com.adobe.granite.crypto.CryptoSupport;
import org.apache.commons.lang3.StringUtils;
import org.apache.felix.scr.annotations.Component;
import org.apache.felix.scr.annotations.Reference;
import org.apache.felix.scr.annotations.ReferenceCardinality;
import org.apache.felix.scr.annotations.ReferencePolicy;
import org.apache.felix.scr.annotations.Service;
import org.apache.sling.api.SlingHttpServletRequest;
import org.apache.sling.api.resource.ResourceResolver;
import org.apache.sling.servlets.post.Modification;
import org.apache.sling.servlets.post.SlingPostProcessor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.jcr.Property;
import javax.jcr.RepositoryException;
import javax.jcr.Session;
import java.util.Collection;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;


@Component
@Service
public class PasswordPropertySavePostProcessor implements SlingPostProcessor {

	private static final Logger LOGGER = LoggerFactory.getLogger(PasswordPropertySavePostProcessor.class);

	private static final String PATH_SEPARATOR = "/";

	@Reference(
			bind = "bind",
			unbind = "unbind",
			cardinality = ReferenceCardinality.OPTIONAL_MULTIPLE,
			referenceInterface = PasswordFiledEncryptFilter.class,
			policy = ReferencePolicy.DYNAMIC)
	private Collection<PasswordFiledEncryptFilter> encryptionFilters = ConcurrentHashMap.newKeySet();

	@Reference
	private CryptoSupport cryptoSupport;

	@Override
	public void process(SlingHttpServletRequest slingHttpServletRequest, List<Modification> list) {
		List<String> propertiesToEncrypt = list.stream()
				.filter(this::isSupported)
				.map(Modification::getSource)
				.collect(Collectors.toList());

		ResourceResolver resourceResolver = slingHttpServletRequest.getResourceResolver();
		Session session = resourceResolver.adaptTo(Session.class);

		if (session == null) {
			LOGGER.error("Failed to create session.");
			return;
		}

		for (String propertyPath : propertiesToEncrypt) {
			try {
				this.encryptProperty(session, propertyPath);
			} catch (CryptoException | RepositoryException e) {
				LOGGER.error("Failed to encrypt property {}", propertyPath, e);
			}
		}

	}

	private boolean isSupported(Modification modification) {
		return encryptionFilters
			.stream()
			.filter(filter -> filter.isSupported(modification.getSource()))
			.findFirst()
			.isPresent();
	}


	private void encryptProperty(Session session, String propertyPath) throws CryptoException, RepositoryException {
		Property propertyToBeProtected = session.getProperty(propertyPath);
		if (propertyToBeProtected != null) {
			String propertyValue = StringUtils.defaultString(propertyToBeProtected.getString());
			if (!cryptoSupport.isProtected(propertyValue)) {
				LOGGER.info("Encrypting property: '{}'", propertyPath);
				String encryptedPropertyValue = cryptoSupport.protect(propertyValue);
				propertyToBeProtected.setValue(encryptedPropertyValue);
				LOGGER.info("Property '{}' encrypted", propertyPath);
			}
		}

	}

	private String getPropertyNodePath(String propertyPath) {
		return StringUtils.substringBeforeLast(propertyPath, PATH_SEPARATOR);
	}

	private String getPropertyName(String propertyPath) {
		return StringUtils.substringAfterLast(propertyPath, PATH_SEPARATOR);
	}

	protected void bind(PasswordFiledEncryptFilter filter) {
		encryptionFilters.add(filter);
	}

	protected void unbind(PasswordFiledEncryptFilter filter) {
		encryptionFilters.remove(filter);
	}
}
