package com.devicehive.service;


import com.devicehive.configuration.Messages;
import com.devicehive.dao.OAuthClientDAO;
import com.devicehive.exceptions.HiveException;
import com.devicehive.model.OAuthClient;
import com.devicehive.model.updates.OAuthClientUpdate;
import com.devicehive.service.helpers.DefaultPasswordProcessor;
import com.devicehive.service.helpers.PasswordProcessor;

import java.util.List;

import javax.ejb.EJB;
import javax.ejb.Stateless;
import javax.ejb.TransactionAttribute;
import javax.ejb.TransactionAttributeType;
import javax.validation.constraints.NotNull;

import static javax.servlet.http.HttpServletResponse.SC_BAD_REQUEST;
import static javax.servlet.http.HttpServletResponse.SC_FORBIDDEN;
import static javax.servlet.http.HttpServletResponse.SC_NOT_FOUND;

@Stateless
@EJB(beanInterface = OAuthClientService.class, name = "OAuthClientService")
public class OAuthClientService {

    @EJB
    private OAuthClientDAO clientDAO;
    private PasswordProcessor secretGenerator = new DefaultPasswordProcessor();

    @TransactionAttribute(TransactionAttributeType.SUPPORTS)
    public OAuthClient get(@NotNull Long id) {
        return clientDAO.get(id);
    }

    @TransactionAttribute(TransactionAttributeType.NOT_SUPPORTED)
    public List<OAuthClient> get(String name,
                                 String namePattern,
                                 String domain,
                                 String oauthId,
                                 String sortField,
                                 Boolean sortOrderAsc,
                                 Integer take,
                                 Integer skip) {
        return clientDAO.list(name, namePattern, domain, oauthId, sortField, sortOrderAsc, take, skip);
    }

    @TransactionAttribute(TransactionAttributeType.NOT_SUPPORTED)
    public OAuthClient insert(OAuthClient client) {
        if (client.getId() != null) {
            throw new HiveException(Messages.ID_NOT_ALLOWED, SC_BAD_REQUEST);
        }
        OAuthClient clientWithExistingID = clientDAO.get(client.getOauthId());
        if (clientWithExistingID != null) {
            throw new HiveException(Messages.DUPLICATE_OAUTH_ID, SC_FORBIDDEN);
        }
        client.setOauthSecret(secretGenerator.generateSalt());
        clientDAO.insert(client);
        return client;
    }

    public boolean update(OAuthClientUpdate client, Long clientId) {
        OAuthClient existing = clientDAO.get(clientId);
        if (existing == null) {
            throw new HiveException(String.format(Messages.OAUTH_CLIENT_NOT_FOUND, clientId), SC_NOT_FOUND);
        }
        if (client == null) {
            return true;
        }
        if (client.getOauthId() != null && !client.getOauthId().getValue().equals(existing.getOauthId())) {
            OAuthClient existingWithOAuthID = clientDAO.get(client.getOauthId().getValue());
            if (existingWithOAuthID != null) {
                throw new HiveException(Messages.DUPLICATE_OAUTH_ID, SC_FORBIDDEN);
            }
        }
        if (client.getName() != null) {
            existing.setName(client.getName().getValue());
        }
        if (client.getDomain() != null) {
            existing.setDomain(client.getDomain().getValue());
        }
        if (client.getSubnet() != null) {
            existing.setSubnet(client.getSubnet().getValue());
        }
        if (client.getRedirectUri() != null) {
            existing.setRedirectUri(client.getRedirectUri().getValue());
        }
        if (client.getOauthId() != null) {
            existing.setOauthId(client.getOauthId().getValue());
        }
        return true;
    }

    @TransactionAttribute(TransactionAttributeType.NOT_SUPPORTED)
    public boolean delete(@NotNull Long id) {
        return clientDAO.delete(id);
    }

    @TransactionAttribute(TransactionAttributeType.SUPPORTS)
    public OAuthClient getByOAuthID(String oauthID) {
        return clientDAO.get(oauthID);
    }

    @TransactionAttribute(TransactionAttributeType.NOT_SUPPORTED)
    public OAuthClient authenticate(@NotNull String id, @NotNull String secret) {
        return clientDAO.get(id, secret);
    }

    @TransactionAttribute(TransactionAttributeType.SUPPORTS)
    public OAuthClient getByName(String name) {
        return clientDAO.getByName(name);
    }
}

