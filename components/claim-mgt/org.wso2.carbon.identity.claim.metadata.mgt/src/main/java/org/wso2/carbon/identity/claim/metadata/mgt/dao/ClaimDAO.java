/*
 * Copyright (c) 2016, WSO2 Inc. (http://www.wso2.org) All Rights Reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.wso2.carbon.identity.claim.metadata.mgt.dao;

import org.apache.commons.lang.StringUtils;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.wso2.carbon.identity.claim.metadata.mgt.exception.ClaimMetadataClientException;
import org.wso2.carbon.identity.claim.metadata.mgt.exception.ClaimMetadataException;
import org.wso2.carbon.identity.claim.metadata.mgt.exception.DuplicateClaimException;
import org.wso2.carbon.identity.claim.metadata.mgt.model.Claim;
import org.wso2.carbon.identity.claim.metadata.mgt.model.ClaimDialect;
import org.wso2.carbon.identity.claim.metadata.mgt.util.SQLConstants;
import org.wso2.carbon.identity.core.util.IdentityDatabaseUtil;
import org.wso2.carbon.utils.DBUtils;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.SQLIntegrityConstraintViolationException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.wso2.carbon.identity.claim.metadata.mgt.util.ClaimConstants.ErrorMessage.ERROR_CODE_MAPPED_TO_INVALID_LOCAL_CLAIM_URI;

/**
 *
 * Data access object for org.wso2.carbon.identity.claim.metadata.mgt.model.Claim
 *
 */
public class ClaimDAO {

    private static final Log log = LogFactory.getLog(ClaimDAO.class);

    public Map<Integer, Claim> getClaims(Connection connection, String claimDialectURI, int tenantId) throws
            ClaimMetadataException {

        Map<Integer, Claim> claimMap = new HashMap<>();

        PreparedStatement prepStmt = null;
        ResultSet rs = null;

        String query = SQLConstants.GET_CLAIMS_BY_DIALECT;

        try {
            prepStmt = connection.prepareStatement(query);
            prepStmt.setString(1, claimDialectURI);
            prepStmt.setInt(2, tenantId);
            prepStmt.setInt(3, tenantId);
            rs = prepStmt.executeQuery(); // TODO : Get the logic reviewed : using executeQuery in a transaction.

            while (rs.next()) {
                String claimURI = rs.getString(SQLConstants.CLAIM_URI_COLUMN);
                int claimId = rs.getInt(SQLConstants.ID_COLUMN);
                claimMap.put(claimId, new Claim(claimDialectURI, claimURI));
            }

        } catch (SQLException e) {
            throw new ClaimMetadataException("Error while listing claims for dialect " + claimDialectURI, e);
        } finally {
            IdentityDatabaseUtil.closeResultSet(rs);
            IdentityDatabaseUtil.closeStatement(prepStmt);
        }

        return claimMap;
    }

    public int addClaim(Connection connection, String claimDialectURI, String claimURI, int tenantId) throws
            ClaimMetadataException {

        PreparedStatement prepStmt = null;
        ResultSet rs = null;

        int claimId = 0;
        String query = SQLConstants.ADD_CLAIM;
        try {
            String dbProductName = connection.getMetaData().getDatabaseProductName();
            prepStmt = connection.prepareStatement(query, new String[]{DBUtils.getConvertedAutoGeneratedColumnName
                    (dbProductName, SQLConstants.ID_COLUMN)});

            prepStmt.setString(1, claimDialectURI);
            prepStmt.setInt(2, tenantId);
            prepStmt.setString(3, claimURI);
            prepStmt.setInt(4, tenantId);
            prepStmt.executeUpdate();

            rs = prepStmt.getGeneratedKeys();

            if (rs.next()) {
                claimId = rs.getInt(1);
            }
        } catch (SQLException e) {
            if (isSQLIntegrityConstraintViolation(e) &&
                    isClaimAlreadyPersisted(connection, claimDialectURI, claimURI, tenantId)) {
                    throw new DuplicateClaimException(
                            "Claim " + claimURI + " in dialect " + claimDialectURI + " is already persisted", e);
            } else {
                throw new ClaimMetadataException(
                        "Error while adding claim " + claimURI + " to dialect " + claimDialectURI, e);
            }
        } finally {
            IdentityDatabaseUtil.closeResultSet(rs);
            IdentityDatabaseUtil.closeStatement(prepStmt);
        }

        return claimId;
    }

    public void removeClaim(String claimDialectURI, String localClaimURI, int tenantId) throws
            ClaimMetadataException {

        Connection connection = IdentityDatabaseUtil.getDBConnection();
        PreparedStatement prepStmt = null;

        String query = SQLConstants.REMOVE_CLAIM;
        try {
            prepStmt = connection.prepareStatement(query);
            prepStmt.setString(1, claimDialectURI);
            prepStmt.setInt(2, tenantId);
            prepStmt.setString(3, localClaimURI);
            prepStmt.setInt(4, tenantId);
            prepStmt.executeUpdate();
            IdentityDatabaseUtil.commitTransaction(connection);
        } catch (SQLException e) {
            IdentityDatabaseUtil.rollbackTransaction(connection);
            throw new ClaimMetadataException("Error while deleting claim " + localClaimURI + " from dialect" +
                    claimDialectURI, e);
        } finally {
            IdentityDatabaseUtil.closeStatement(prepStmt);
            IdentityDatabaseUtil.closeConnection(connection);
        }
    }

    public int getClaimId(Connection connection, String claimDialectURI, String claimURI, int tenantId) throws
            ClaimMetadataException {

        PreparedStatement prepStmt = null;
        ResultSet rs = null;

        int claimId = 0;
        String query = SQLConstants.GET_CLAIM_ID;
        try {
            prepStmt = connection.prepareStatement(query);
            prepStmt.setString(1, claimDialectURI);
            prepStmt.setInt(2, tenantId);
            prepStmt.setString(3, claimURI);
            prepStmt.setInt(4, tenantId);
            rs = prepStmt.executeQuery();

            while (rs.next()) {
                claimId = rs.getInt(SQLConstants.ID_COLUMN);
            }
        } catch (SQLException e) {
            throw new ClaimMetadataException("Error while retrieving ID for claim " + claimURI + " in dialect "
                    + claimDialectURI, e);
        } finally {
            IdentityDatabaseUtil.closeResultSet(rs);
            IdentityDatabaseUtil.closeStatement(prepStmt);
        }

        if (claimId == 0) {
            // TODO : Throw runtime exception?
            throw new ClaimMetadataClientException(ERROR_CODE_MAPPED_TO_INVALID_LOCAL_CLAIM_URI.getCode(),
                    String.format(ERROR_CODE_MAPPED_TO_INVALID_LOCAL_CLAIM_URI.getMessage(), claimURI,
                            claimDialectURI));
        }

        return claimId;
    }

    public Map<String, String> getClaimProperties(Connection connection, int claimId, int tenantId)
            throws ClaimMetadataException {

        Map<String, String> claimProperties = new HashMap<>();

        String query = SQLConstants.GET_CLAIM_PROPERTIES;

        try (PreparedStatement prepStmt = connection.prepareStatement(query)) {
            prepStmt.setInt(1, claimId);
            prepStmt.setInt(2, tenantId);

            try (ResultSet rs = prepStmt.executeQuery()) {
                while (rs.next()) {
                    String claimPropertyName = rs.getString(SQLConstants.PROPERTY_NAME_COLUMN);
                    String claimPropertyValue = rs.getString(SQLConstants.PROPERTY_VALUE_COLUMN);

                    claimProperties.put(claimPropertyName, claimPropertyValue);
                }
            }
        } catch (SQLException e) {
            throw new ClaimMetadataException("Error while retrieving claim properties", e);
        }

        return claimProperties;
    }

    public void addClaimProperties(Connection connection, int claimId, Map<String, String> claimProperties,
            int tenantId) throws ClaimMetadataException {

        if (claimId > 0 && claimProperties != null) {
            String query = SQLConstants.ADD_CLAIM_PROPERTY;
            try (PreparedStatement prepStmt = connection.prepareStatement(query);) {
                for (Map.Entry<String, String> property : claimProperties.entrySet()) {
                    prepStmt.setInt(1, claimId);
                    prepStmt.setString(2, property.getKey());
                    prepStmt.setString(3, property.getValue());
                    prepStmt.setInt(4, tenantId);
                    prepStmt.addBatch();
                }

                prepStmt.executeBatch();
            } catch (SQLException e) {
                throw new ClaimMetadataException("Error while adding claim properties", e);
            }
        }
    }

    protected void deleteClaimProperties(Connection connection, int claimId, int tenantId)
            throws ClaimMetadataException {

        String query = SQLConstants.DELETE_CLAIM_PROPERTY;
        try (PreparedStatement prepStmt = connection.prepareStatement(query)) {
            prepStmt.setInt(1, claimId);
            prepStmt.setInt(2, tenantId);
            prepStmt.execute();
        } catch (SQLException e) {
            throw new ClaimMetadataException("Error while deleting claim properties", e);
        }
    }

    /**
     * Checks whether the specified claim is already persisted
     *  Existence of a valid claim ID (id > 0) for given claimDialectURI and claimURI pair, verifies that the claim
     *  is already persisted
     * @param connection connection
     * @param claimDialectURI dialectURI
     * @param claimURI claimURI
     * @param tenantId tenantID
     * @return
     * @throws ClaimMetadataException
     */
    private boolean isClaimAlreadyPersisted(Connection connection, String claimDialectURI, String claimURI, int tenantId)
            throws ClaimMetadataException {
        return getClaimId(connection, claimDialectURI, claimURI, tenantId) > 0;
    }

    /**
     * Checks whether the sqlexeption caught is due to a constraint violation error.
     * In mssql, constraint violation error is wrapped in an SQLServerException instead of an
     * SQLIntegrityConstraintViolationException. So for mssql we are checking the error code of the
     * exception thrown to identify constrant violation errors in mssql.
     * @param e sql exception caught
     * @return
     */
    private boolean isSQLIntegrityConstraintViolation(SQLException e) {
        return e instanceof SQLIntegrityConstraintViolationException
                || e.getErrorCode() == SQLConstants.UNIQUE_CONTRAINT_VIOLATION_ERROR_CODE;
    }
}
