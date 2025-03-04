/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.apache.shardingsphere.mode.manager;

import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.apache.shardingsphere.infra.config.database.DatabaseConfiguration;
import org.apache.shardingsphere.infra.config.database.impl.DataSourceProvidedDatabaseConfiguration;
import org.apache.shardingsphere.infra.config.props.ConfigurationProperties;
import org.apache.shardingsphere.infra.config.props.ConfigurationPropertyKey;
import org.apache.shardingsphere.infra.config.rule.RuleConfiguration;
import org.apache.shardingsphere.infra.database.type.DatabaseType;
import org.apache.shardingsphere.infra.database.type.DatabaseTypeEngine;
import org.apache.shardingsphere.infra.datasource.props.DataSourceProperties;
import org.apache.shardingsphere.infra.executor.kernel.ExecutorEngine;
import org.apache.shardingsphere.infra.instance.InstanceContext;
import org.apache.shardingsphere.infra.metadata.ShardingSphereMetaData;
import org.apache.shardingsphere.infra.metadata.data.ShardingSphereDatabaseData;
import org.apache.shardingsphere.infra.metadata.data.ShardingSphereSchemaData;
import org.apache.shardingsphere.infra.metadata.data.ShardingSphereTableData;
import org.apache.shardingsphere.infra.metadata.database.ShardingSphereDatabase;
import org.apache.shardingsphere.infra.metadata.database.resource.ShardingSphereResourceMetaData;
import org.apache.shardingsphere.infra.metadata.database.rule.ShardingSphereRuleMetaData;
import org.apache.shardingsphere.infra.metadata.database.schema.SchemaManager;
import org.apache.shardingsphere.infra.metadata.database.schema.builder.GenericSchemaBuilder;
import org.apache.shardingsphere.infra.metadata.database.schema.builder.GenericSchemaBuilderMaterial;
import org.apache.shardingsphere.infra.metadata.database.schema.model.ShardingSphereColumn;
import org.apache.shardingsphere.infra.metadata.database.schema.model.ShardingSphereSchema;
import org.apache.shardingsphere.infra.metadata.database.schema.model.ShardingSphereTable;
import org.apache.shardingsphere.infra.metadata.database.schema.model.ShardingSphereView;
import org.apache.shardingsphere.infra.rule.builder.global.GlobalRulesBuilder;
import org.apache.shardingsphere.infra.rule.identifier.type.MutableDataNodeRule;
import org.apache.shardingsphere.infra.rule.identifier.type.ResourceHeldRule;
import org.apache.shardingsphere.infra.rule.identifier.type.TableContainedRule;
import org.apache.shardingsphere.infra.state.cluster.ClusterState;
import org.apache.shardingsphere.infra.state.cluster.ClusterStateContext;
import org.apache.shardingsphere.infra.yaml.data.pojo.YamlShardingSphereRowData;
import org.apache.shardingsphere.infra.yaml.data.swapper.YamlShardingSphereRowDataSwapper;
import org.apache.shardingsphere.metadata.factory.ExternalMetaDataFactory;
import org.apache.shardingsphere.metadata.factory.InternalMetaDataFactory;
import org.apache.shardingsphere.metadata.persist.MetaDataBasedPersistService;
import org.apache.shardingsphere.mode.manager.switcher.ResourceSwitchManager;
import org.apache.shardingsphere.mode.manager.switcher.SwitchingResource;
import org.apache.shardingsphere.mode.metadata.MetaDataContexts;

import javax.sql.DataSource;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Optional;
import java.util.Properties;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Collectors;

/**
 * Context manager.
 */
@Getter
@Slf4j
public final class ContextManager implements AutoCloseable {
    
    private final AtomicReference<MetaDataContexts> metaDataContexts;
    
    private final InstanceContext instanceContext;
    
    private final ExecutorEngine executorEngine;
    
    private final ClusterStateContext clusterStateContext = new ClusterStateContext();
    
    public ContextManager(final MetaDataContexts metaDataContexts, final InstanceContext instanceContext) {
        this.metaDataContexts = new AtomicReference<>(metaDataContexts);
        this.instanceContext = instanceContext;
        executorEngine = ExecutorEngine.createExecutorEngineWithSize(metaDataContexts.getMetaData().getProps().<Integer>getValue(ConfigurationPropertyKey.KERNEL_EXECUTOR_SIZE));
    }
    
    /**
     * Get meta data contexts.
     * 
     * @return meta data contexts
     */
    public MetaDataContexts getMetaDataContexts() {
        return metaDataContexts.get();
    }
    
    /**
     * Renew meta data contexts.
     * 
     * @param metaDataContexts meta data contexts
     */
    public synchronized void renewMetaDataContexts(final MetaDataContexts metaDataContexts) {
        this.metaDataContexts.set(metaDataContexts);
    }
    
    /**
     * Get data source map.
     * 
     * @param databaseName database name
     * @return data source map
     */
    public Map<String, DataSource> getDataSourceMap(final String databaseName) {
        return metaDataContexts.get().getMetaData().getDatabase(databaseName).getResourceMetaData().getDataSources();
    }
    
    /**
     * Add database.
     * 
     * @param databaseName database name
     */
    public synchronized void addDatabase(final String databaseName) {
        if (metaDataContexts.get().getMetaData().containsDatabase(databaseName)) {
            return;
        }
        DatabaseType protocolType = DatabaseTypeEngine.getProtocolType(Collections.emptyMap(), metaDataContexts.get().getMetaData().getProps());
        metaDataContexts.get().getMetaData().addDatabase(databaseName, protocolType);
    }
    
    /**
     * Drop database.
     * 
     * @param databaseName database name
     */
    public synchronized void dropDatabase(final String databaseName) {
        if (!metaDataContexts.get().getMetaData().containsDatabase(databaseName)) {
            return;
        }
        metaDataContexts.get().getMetaData().dropDatabase(metaDataContexts.get().getMetaData().getDatabase(databaseName).getName());
    }
    
    /**
     * Add schema.
     * 
     * @param databaseName database name
     * @param schemaName schema name
     */
    public synchronized void addSchema(final String databaseName, final String schemaName) {
        if (metaDataContexts.get().getMetaData().getDatabase(databaseName).containsSchema(schemaName)) {
            return;
        }
        metaDataContexts.get().getMetaData().getDatabase(databaseName).putSchema(schemaName, new ShardingSphereSchema());
    }
    
    /**
     * Drop schema.
     * 
     * @param databaseName database name
     * @param schemaName schema name
     */
    public synchronized void dropSchema(final String databaseName, final String schemaName) {
        if (!metaDataContexts.get().getMetaData().getDatabase(databaseName).containsSchema(schemaName)) {
            return;
        }
        metaDataContexts.get().getMetaData().getDatabase(databaseName).removeSchema(schemaName);
    }
    
    /**
     * Alter schema.
     * 
     * @param databaseName database name
     * @param schemaName schema name
     * @param toBeDeletedTableName to be deleted table name
     * @param toBeDeletedViewName to be deleted view name
     */
    public synchronized void alterSchema(final String databaseName, final String schemaName, final String toBeDeletedTableName, final String toBeDeletedViewName) {
        Optional.ofNullable(toBeDeletedTableName).ifPresent(optional -> dropTable(databaseName, schemaName, optional));
        Optional.ofNullable(toBeDeletedViewName).ifPresent(optional -> dropView(databaseName, schemaName, optional));
    }
    
    /**
     * Alter schema.
     * 
     * @param databaseName database name
     * @param schemaName schema name
     * @param toBeChangedTable to be changed table
     * @param toBeChangedView to be changed view
     */
    public synchronized void alterSchema(final String databaseName, final String schemaName, final ShardingSphereTable toBeChangedTable, final ShardingSphereView toBeChangedView) {
        if (!metaDataContexts.get().getMetaData().containsDatabase(databaseName) || !metaDataContexts.get().getMetaData().getDatabase(databaseName).containsSchema(schemaName)) {
            return;
        }
        Optional.ofNullable(toBeChangedTable).ifPresent(optional -> alterTable(databaseName, schemaName, optional));
        Optional.ofNullable(toBeChangedView).ifPresent(optional -> alterView(databaseName, schemaName, optional));
    }
    
    private synchronized void dropTable(final String databaseName, final String schemaName, final String toBeDeletedTableName) {
        metaDataContexts.get().getMetaData().getDatabase(databaseName).getSchema(schemaName).removeTable(toBeDeletedTableName);
        metaDataContexts.get().getMetaData().getDatabase(databaseName).getRuleMetaData().getRules().stream().filter(MutableDataNodeRule.class::isInstance).findFirst()
                .ifPresent(optional -> ((MutableDataNodeRule) optional).remove(schemaName, toBeDeletedTableName));
    }
    
    private synchronized void dropView(final String databaseName, final String schemaName, final String toBeDeletedViewName) {
        metaDataContexts.get().getMetaData().getDatabase(databaseName).getSchema(schemaName).removeView(toBeDeletedViewName);
        metaDataContexts.get().getMetaData().getDatabase(databaseName).getRuleMetaData().getRules().stream().filter(MutableDataNodeRule.class::isInstance).findFirst()
                .ifPresent(optional -> ((MutableDataNodeRule) optional).remove(schemaName, toBeDeletedViewName));
    }
    
    private synchronized void alterTable(final String databaseName, final String schemaName, final ShardingSphereTable beBoChangedTable) {
        ShardingSphereDatabase database = metaDataContexts.get().getMetaData().getDatabase(databaseName);
        if (isSingleTable(database, beBoChangedTable.getName())) {
            database.reloadRules(MutableDataNodeRule.class);
        }
        database.getSchema(schemaName).putTable(beBoChangedTable.getName(), beBoChangedTable);
    }
    
    private synchronized void alterView(final String databaseName, final String schemaName, final ShardingSphereView beBoChangedView) {
        ShardingSphereDatabase database = metaDataContexts.get().getMetaData().getDatabase(databaseName);
        if (isSingleTable(database, beBoChangedView.getName())) {
            database.reloadRules(MutableDataNodeRule.class);
        }
        database.getSchema(schemaName).putView(beBoChangedView.getName(), beBoChangedView);
    }
    
    private boolean isSingleTable(final ShardingSphereDatabase database, final String tableName) {
        return database.getRuleMetaData().findRules(TableContainedRule.class).stream().noneMatch(each -> each.getDistributedTableMapper().contains(tableName));
    }
    
    /**
     * Alter rule configuration.
     * 
     * @param databaseName database name
     * @param ruleConfigs rule configurations
     */
    @SuppressWarnings("rawtypes")
    public synchronized void alterRuleConfiguration(final String databaseName, final Collection<RuleConfiguration> ruleConfigs) {
        try {
            Collection<ResourceHeldRule> staleResourceHeldRules = getStaleResourceHeldRules(databaseName);
            staleResourceHeldRules.forEach(ResourceHeldRule::closeStaleResource);
            MetaDataContexts reloadMetaDataContexts = createMetaDataContexts(databaseName, false, null, ruleConfigs);
            alterSchemaMetaData(databaseName, reloadMetaDataContexts.getMetaData().getDatabase(databaseName), metaDataContexts.get().getMetaData().getDatabase(databaseName));
            metaDataContexts.set(reloadMetaDataContexts);
            metaDataContexts.get().getMetaData().getDatabase(databaseName).getSchemas().putAll(newShardingSphereSchemas(metaDataContexts.get().getMetaData().getDatabase(databaseName)));
        } catch (final SQLException ex) {
            log.error("Alter database: {} rule configurations failed", databaseName, ex);
        }
    }
    
    /**
     * Alter schema meta data.
     * 
     * @param databaseName database name
     * @param reloadDatabase reload database
     * @param currentDatabase current database
     */
    public synchronized void alterSchemaMetaData(final String databaseName, final ShardingSphereDatabase reloadDatabase, final ShardingSphereDatabase currentDatabase) {
        Map<String, ShardingSphereSchema> toBeAlterSchemas = SchemaManager.getToBeDeletedTablesBySchemas(reloadDatabase.getSchemas(), currentDatabase.getSchemas());
        Map<String, ShardingSphereSchema> toBeAddedSchemas = SchemaManager.getToBeAddedTablesBySchemas(reloadDatabase.getSchemas(), currentDatabase.getSchemas());
        toBeAddedSchemas.forEach((key, value) -> metaDataContexts.get().getPersistService().getDatabaseMetaDataService().persist(databaseName, key, value));
        toBeAlterSchemas.forEach((key, value) -> metaDataContexts.get().getPersistService().getDatabaseMetaDataService().delete(databaseName, key, value));
    }
    
    /**
     * Alter data source configuration.
     * 
     * @param databaseName database name
     * @param dataSourcePropsMap altered data source properties map
     */
    @SuppressWarnings("rawtypes")
    public synchronized void alterDataSourceConfiguration(final String databaseName, final Map<String, DataSourceProperties> dataSourcePropsMap) {
        try {
            Collection<ResourceHeldRule> staleResourceHeldRules = getStaleResourceHeldRules(databaseName);
            staleResourceHeldRules.forEach(ResourceHeldRule::closeStaleResource);
            SwitchingResource switchingResource =
                    new ResourceSwitchManager().createByAlterDataSourceProps(metaDataContexts.get().getMetaData().getDatabase(databaseName).getResourceMetaData(), dataSourcePropsMap);
            metaDataContexts.get().getMetaData().getDatabases().putAll(renewDatabase(metaDataContexts.get().getMetaData().getDatabase(databaseName), switchingResource));
            // TODO Remove this logic when issue #22887 are finished.
            MetaDataContexts reloadMetaDataContexts = createMetaDataContexts(databaseName, false, switchingResource, null);
            reloadMetaDataContexts.getMetaData().getDatabase(databaseName).getSchemas().forEach((schemaName, schema) -> reloadMetaDataContexts.getPersistService().getDatabaseMetaDataService()
                    .persist(reloadMetaDataContexts.getMetaData().getDatabase(databaseName).getName(), schemaName, schema));
            Optional.ofNullable(reloadMetaDataContexts.getShardingSphereData().getDatabaseData().get(databaseName))
                    .ifPresent(optional -> optional.getSchemaData().forEach((schemaName, schemaData) -> reloadMetaDataContexts.getPersistService().getShardingSphereDataPersistService()
                            .persist(databaseName, schemaName, schemaData, metaDataContexts.get().getMetaData().getDatabases())));
            alterSchemaMetaData(databaseName, reloadMetaDataContexts.getMetaData().getDatabase(databaseName), metaDataContexts.get().getMetaData().getDatabase(databaseName));
            metaDataContexts.set(reloadMetaDataContexts);
            metaDataContexts.get().getMetaData().getDatabases().putAll(newShardingSphereDatabase(metaDataContexts.get().getMetaData().getDatabase(databaseName)));
            switchingResource.closeStaleDataSources();
        } catch (final SQLException ex) {
            log.error("Alter database: {} data source configuration failed", databaseName, ex);
        }
    }
    
    /**
     * Renew ShardingSphere databases.
     * 
     * @param database database
     * @param resource resource
     * @return ShardingSphere databases
     */
    public synchronized Map<String, ShardingSphereDatabase> renewDatabase(final ShardingSphereDatabase database, final SwitchingResource resource) {
        Map<String, DataSource> newDataSource =
                database.getResourceMetaData().getDataSources().entrySet().stream().filter(entry -> !resource.getStaleDataSources().containsKey(entry.getKey()))
                        .collect(Collectors.toMap(Entry::getKey, Entry::getValue, (oldValue, currentValue) -> oldValue, LinkedHashMap::new));
        return Collections.singletonMap(database.getName().toLowerCase(),
                new ShardingSphereDatabase(database.getName(), database.getProtocolType(), new ShardingSphereResourceMetaData(database.getName(), newDataSource),
                        database.getRuleMetaData(), database.getSchemas()));
    }
    
    /**
     * Alter data source and rule configuration.
     * 
     * @param databaseName database name
     * @param dataSourcePropsMap data source props map
     * @param ruleConfigs rule configurations
     */
    @SuppressWarnings("rawtypes")
    public synchronized void alterDataSourceAndRuleConfiguration(final String databaseName,
                                                                 final Map<String, DataSourceProperties> dataSourcePropsMap, final Collection<RuleConfiguration> ruleConfigs) {
        try {
            Collection<ResourceHeldRule> staleResourceHeldRules = getStaleResourceHeldRules(databaseName);
            staleResourceHeldRules.forEach(ResourceHeldRule::closeStaleResource);
            SwitchingResource switchingResource = new ResourceSwitchManager().create(metaDataContexts.get().getMetaData().getDatabase(databaseName).getResourceMetaData(), dataSourcePropsMap);
            metaDataContexts.set(createMetaDataContexts(databaseName, true, switchingResource, ruleConfigs));
            switchingResource.closeStaleDataSources();
        } catch (final SQLException ex) {
            log.error("Alter database: {} data source and rule configuration failed", databaseName, ex);
        }
    }
    
    @SuppressWarnings("rawtypes")
    private Collection<ResourceHeldRule> getStaleResourceHeldRules(final String databaseName) {
        Collection<ResourceHeldRule> result = new LinkedList<>();
        result.addAll(metaDataContexts.get().getMetaData().getDatabase(databaseName).getRuleMetaData().findRules(ResourceHeldRule.class));
        result.addAll(metaDataContexts.get().getMetaData().getGlobalRuleMetaData().findRules(ResourceHeldRule.class));
        return result;
    }
    
    /**
     * Create meta data contexts.
     * 
     * @param databaseName database name
     * @param internalLoadMetaData internal load meta data
     * @param switchingResource switching resource
     * @param ruleConfigs rule configs
     * @return MetaDataContexts meta data contexts
     * @throws SQLException SQL exception
     */
    public synchronized MetaDataContexts createMetaDataContexts(final String databaseName, final boolean internalLoadMetaData, final SwitchingResource switchingResource,
                                                                final Collection<RuleConfiguration> ruleConfigs) throws SQLException {
        Map<String, ShardingSphereDatabase> changedDatabases = createChangedDatabases(databaseName, internalLoadMetaData, switchingResource, ruleConfigs);
        ConfigurationProperties props = metaDataContexts.get().getMetaData().getProps();
        ShardingSphereRuleMetaData changedGlobalMetaData = new ShardingSphereRuleMetaData(
                GlobalRulesBuilder.buildRules(metaDataContexts.get().getMetaData().getGlobalRuleMetaData().getConfigurations(), changedDatabases, props));
        return newMetaDataContexts(new ShardingSphereMetaData(changedDatabases, changedGlobalMetaData, props));
    }
    
    private MetaDataContexts createMetaDataContexts(final String databaseName, final SwitchingResource switchingResource) throws SQLException {
        MetaDataBasedPersistService metaDataPersistService = metaDataContexts.get().getPersistService();
        Map<String, ShardingSphereDatabase> changedDatabases = createChangedDatabases(databaseName, false,
                switchingResource, metaDataPersistService.getDatabaseRulePersistService().load(databaseName));
        ConfigurationProperties props = new ConfigurationProperties(metaDataPersistService.getPropsService().load());
        ShardingSphereRuleMetaData changedGlobalMetaData = new ShardingSphereRuleMetaData(
                GlobalRulesBuilder.buildRules(metaDataPersistService.getGlobalRuleService().load(), changedDatabases, props));
        return newMetaDataContexts(new ShardingSphereMetaData(changedDatabases, changedGlobalMetaData, props));
    }
    
    private MetaDataContexts newMetaDataContexts(final ShardingSphereMetaData metaData) {
        return new MetaDataContexts(metaDataContexts.get().getPersistService(), metaData);
    }
    
    /**
     * Create changed databases.
     * 
     * @param databaseName database name
     * @param internalLoadMetaData internal load meta data
     * @param switchingResource switching resource
     * @param ruleConfigs rule configs
     * @return ShardingSphere databases
     * @throws SQLException SQL exception
     */
    public synchronized Map<String, ShardingSphereDatabase> createChangedDatabases(final String databaseName, final boolean internalLoadMetaData,
                                                                                   final SwitchingResource switchingResource, final Collection<RuleConfiguration> ruleConfigs) throws SQLException {
        if (null != switchingResource && !switchingResource.getNewDataSources().isEmpty()) {
            metaDataContexts.get().getMetaData().getDatabase(databaseName).getResourceMetaData().getDataSources().putAll(switchingResource.getNewDataSources());
        }
        Collection<RuleConfiguration> toBeCreatedRuleConfigs = null == ruleConfigs
                ? metaDataContexts.get().getMetaData().getDatabase(databaseName).getRuleMetaData().getConfigurations()
                : ruleConfigs;
        DatabaseConfiguration toBeCreatedDatabaseConfig =
                new DataSourceProvidedDatabaseConfiguration(metaDataContexts.get().getMetaData().getDatabase(databaseName).getResourceMetaData().getDataSources(), toBeCreatedRuleConfigs);
        ShardingSphereDatabase changedDatabase = createChangedDatabase(metaDataContexts.get().getMetaData().getDatabase(databaseName).getName(), internalLoadMetaData,
                metaDataContexts.get().getPersistService(), toBeCreatedDatabaseConfig, metaDataContexts.get().getMetaData().getProps(), instanceContext);
        Map<String, ShardingSphereDatabase> result = new LinkedHashMap<>(metaDataContexts.get().getMetaData().getDatabases());
        changedDatabase.getSchemas().putAll(newShardingSphereSchemas(changedDatabase));
        result.put(databaseName.toLowerCase(), changedDatabase);
        return result;
    }
    
    private ShardingSphereDatabase createChangedDatabase(final String databaseName, final boolean internalLoadMetaData, final MetaDataBasedPersistService persistService,
                                                         final DatabaseConfiguration databaseConfig, final ConfigurationProperties props, final InstanceContext instanceContext) throws SQLException {
        return internalLoadMetaData
                ? InternalMetaDataFactory.create(databaseName, persistService, databaseConfig, props, instanceContext)
                : ExternalMetaDataFactory.create(databaseName, databaseConfig, props, instanceContext);
    }
    
    private Map<String, ShardingSphereSchema> newShardingSphereSchemas(final ShardingSphereDatabase database) {
        Map<String, ShardingSphereSchema> result = new LinkedHashMap<>(database.getSchemas().size(), 1F);
        database.getSchemas().forEach((key, value) -> result.put(key, new ShardingSphereSchema(value.getTables(),
                metaDataContexts.get().getPersistService().getDatabaseMetaDataService().getViewMetaDataPersistService().load(database.getName(), key))));
        return result;
    }
    
    /**
     * Create new ShardingSphere database.
     * 
     * @param originalDatabase original database
     * @return ShardingSphere databases
     */
    public synchronized Map<String, ShardingSphereDatabase> newShardingSphereDatabase(final ShardingSphereDatabase originalDatabase) {
        return Collections.singletonMap(originalDatabase.getName().toLowerCase(), new ShardingSphereDatabase(originalDatabase.getName(),
                originalDatabase.getProtocolType(), originalDatabase.getResourceMetaData(), originalDatabase.getRuleMetaData(),
                metaDataContexts.get().getPersistService().getDatabaseMetaDataService().loadSchemas(originalDatabase.getName())));
    }
    
    /**
     * Alter global rule configuration.
     * 
     * @param ruleConfigs global rule configuration
     */
    @SuppressWarnings("rawtypes")
    public synchronized void alterGlobalRuleConfiguration(final Collection<RuleConfiguration> ruleConfigs) {
        if (ruleConfigs.isEmpty()) {
            return;
        }
        Collection<ResourceHeldRule> staleResourceHeldRules = metaDataContexts.get().getMetaData().getGlobalRuleMetaData().findRules(ResourceHeldRule.class);
        staleResourceHeldRules.forEach(ResourceHeldRule::closeStaleResource);
        ShardingSphereRuleMetaData toBeChangedGlobalRuleMetaData = new ShardingSphereRuleMetaData(
                GlobalRulesBuilder.buildRules(ruleConfigs, metaDataContexts.get().getMetaData().getDatabases(), metaDataContexts.get().getMetaData().getProps()));
        ShardingSphereMetaData toBeChangedMetaData = new ShardingSphereMetaData(
                metaDataContexts.get().getMetaData().getDatabases(), toBeChangedGlobalRuleMetaData, metaDataContexts.get().getMetaData().getProps());
        metaDataContexts.set(newMetaDataContexts(toBeChangedMetaData));
    }
    
    /**
     * Alter properties.
     * 
     * @param props properties to be altered
     */
    public synchronized void alterProperties(final Properties props) {
        ShardingSphereMetaData toBeChangedMetaData = new ShardingSphereMetaData(
                metaDataContexts.get().getMetaData().getDatabases(), metaDataContexts.get().getMetaData().getGlobalRuleMetaData(), new ConfigurationProperties(props));
        metaDataContexts.set(newMetaDataContexts(toBeChangedMetaData));
    }
    
    /**
     * Reload database meta data from governance center.
     * 
     * @param databaseName to be reloaded database name
     */
    public synchronized void reloadDatabaseMetaData(final String databaseName) {
        try {
            ShardingSphereResourceMetaData currentResourceMetaData = metaDataContexts.get().getMetaData().getDatabase(databaseName).getResourceMetaData();
            Map<String, DataSourceProperties> dataSourceProps = metaDataContexts.get().getPersistService().getDataSourceService().load(databaseName);
            SwitchingResource switchingResource = new ResourceSwitchManager().createByAlterDataSourceProps(currentResourceMetaData, dataSourceProps);
            metaDataContexts.get().getMetaData().getDatabases().putAll(renewDatabase(metaDataContexts.get().getMetaData().getDatabase(databaseName), switchingResource));
            MetaDataContexts reloadedMetaDataContexts = createMetaDataContexts(databaseName, switchingResource);
            deletedSchemaNames(databaseName, reloadedMetaDataContexts.getMetaData().getDatabase(databaseName), metaDataContexts.get().getMetaData().getDatabase(databaseName));
            metaDataContexts.set(reloadedMetaDataContexts);
            metaDataContexts.get().getMetaData().getDatabases().values().forEach(each -> each.getSchemas()
                    .forEach((schemaName, schema) -> metaDataContexts.get().getPersistService().getDatabaseMetaDataService().compareAndPersist(each.getName(), schemaName, schema)));
            switchingResource.closeStaleDataSources();
        } catch (final SQLException ex) {
            log.error("Reload database meta data: {} failed", databaseName, ex);
        }
    }
    
    /**
     * Delete schema names.
     * 
     * @param databaseName database name
     * @param reloadDatabase reload database
     * @param currentDatabase current database
     */
    public synchronized void deletedSchemaNames(final String databaseName, final ShardingSphereDatabase reloadDatabase, final ShardingSphereDatabase currentDatabase) {
        SchemaManager.getToBeDeletedSchemaNames(reloadDatabase.getSchemas(), currentDatabase.getSchemas()).keySet()
                .forEach(each -> metaDataContexts.get().getPersistService().getDatabaseMetaDataService().dropSchema(databaseName, each));
    }
    
    /**
     * Reload schema.
     * 
     * @param databaseName database name
     * @param schemaName to be reloaded schema name
     * @param dataSourceName data source name
     */
    public synchronized void reloadSchema(final String databaseName, final String schemaName, final String dataSourceName) {
        try {
            ShardingSphereSchema reloadedSchema = loadSchema(databaseName, schemaName, dataSourceName);
            if (reloadedSchema.getTables().isEmpty()) {
                metaDataContexts.get().getMetaData().getDatabase(databaseName).removeSchema(schemaName);
                metaDataContexts.get().getPersistService().getDatabaseMetaDataService().dropSchema(metaDataContexts.get().getMetaData().getDatabase(databaseName).getName(),
                        schemaName);
            } else {
                metaDataContexts.get().getMetaData().getDatabase(databaseName).putSchema(schemaName, reloadedSchema);
                metaDataContexts.get().getPersistService().getDatabaseMetaDataService()
                        .compareAndPersist(metaDataContexts.get().getMetaData().getDatabase(databaseName).getName(), schemaName, reloadedSchema);
            }
        } catch (final SQLException ex) {
            log.error("Reload meta data of database: {} schema: {} with data source: {} failed", databaseName, schemaName, dataSourceName, ex);
        }
    }
    
    private ShardingSphereSchema loadSchema(final String databaseName, final String schemaName, final String dataSourceName) throws SQLException {
        ShardingSphereDatabase database = metaDataContexts.get().getMetaData().getDatabase(databaseName);
        database.reloadRules(MutableDataNodeRule.class);
        GenericSchemaBuilderMaterial material = new GenericSchemaBuilderMaterial(database.getProtocolType(), database.getResourceMetaData().getStorageTypes(),
                Collections.singletonMap(dataSourceName, database.getResourceMetaData().getDataSources().get(dataSourceName)),
                database.getRuleMetaData().getRules(), metaDataContexts.get().getMetaData().getProps(), schemaName);
        ShardingSphereSchema result = GenericSchemaBuilder.build(material).get(schemaName);
        result.getViews().putAll(metaDataContexts.get().getPersistService().getDatabaseMetaDataService().getViewMetaDataPersistService().load(database.getName(), schemaName));
        return result;
    }
    
    /**
     * Reload table.
     * 
     * @param databaseName database name
     * @param schemaName schema name
     * @param tableName to be reloaded table name
     */
    public synchronized void reloadTable(final String databaseName, final String schemaName, final String tableName) {
        Map<String, DataSource> dataSourceMap = metaDataContexts.get().getMetaData().getDatabase(databaseName).getResourceMetaData().getDataSources();
        try {
            reloadTable(databaseName, schemaName, tableName, dataSourceMap);
        } catch (final SQLException ex) {
            log.error("Reload table: {} meta data of database: {} schema: {} failed", tableName, databaseName, schemaName, ex);
        }
    }
    
    /**
     * Reload table from single data source.
     * 
     * @param databaseName database name
     * @param schemaName schema name
     * @param dataSourceName data source name
     * @param tableName to be reloaded table name
     */
    public synchronized void reloadTable(final String databaseName, final String schemaName, final String dataSourceName, final String tableName) {
        Map<String, DataSource> dataSourceMap = Collections.singletonMap(
                dataSourceName, metaDataContexts.get().getMetaData().getDatabase(databaseName).getResourceMetaData().getDataSources().get(dataSourceName));
        try {
            reloadTable(databaseName, schemaName, tableName, dataSourceMap);
        } catch (final SQLException ex) {
            log.error("Reload table: {} meta data of database: {} schema: {} with data source: {} failed", tableName, databaseName, schemaName, dataSourceName, ex);
        }
    }
    
    private synchronized void reloadTable(final String databaseName, final String schemaName, final String tableName, final Map<String, DataSource> dataSourceMap) throws SQLException {
        ShardingSphereDatabase database = metaDataContexts.get().getMetaData().getDatabase(databaseName);
        GenericSchemaBuilderMaterial material = new GenericSchemaBuilderMaterial(database.getProtocolType(),
                database.getResourceMetaData().getStorageTypes(), dataSourceMap, database.getRuleMetaData().getRules(), metaDataContexts.get().getMetaData().getProps(), schemaName);
        ShardingSphereSchema schema = GenericSchemaBuilder.build(Collections.singletonList(tableName), material).getOrDefault(schemaName, new ShardingSphereSchema());
        if (schema.containsTable(tableName)) {
            alterTable(databaseName, schemaName, schema.getTable(tableName));
        } else {
            dropTable(databaseName, schemaName, tableName);
        }
        metaDataContexts.get().getPersistService().getDatabaseMetaDataService().compareAndPersist(database.getName(), schemaName, database.getSchema(schemaName));
    }
    
    /**
     * Add ShardingSphere database data.
     * 
     * @param databaseName database name
     */
    public synchronized void addShardingSphereDatabaseData(final String databaseName) {
        if (metaDataContexts.get().getShardingSphereData().containsDatabase(databaseName)) {
            return;
        }
        metaDataContexts.get().getShardingSphereData().putDatabase(databaseName, new ShardingSphereDatabaseData());
    }
    
    /**
     * Drop ShardingSphere data database.
     * 
     * @param databaseName database name
     */
    public synchronized void dropShardingSphereDatabaseData(final String databaseName) {
        if (!metaDataContexts.get().getShardingSphereData().containsDatabase(databaseName)) {
            return;
        }
        metaDataContexts.get().getShardingSphereData().dropDatabase(databaseName);
    }
    
    /**
     * Add ShardingSphere schema data.
     * 
     * @param databaseName database name
     * @param schemaName schema name
     */
    public synchronized void addShardingSphereSchemaData(final String databaseName, final String schemaName) {
        if (metaDataContexts.get().getShardingSphereData().getDatabase(databaseName).containsSchema(schemaName)) {
            return;
        }
        metaDataContexts.get().getShardingSphereData().getDatabase(databaseName).putSchema(schemaName, new ShardingSphereSchemaData());
    }
    
    /**
     * Drop ShardingSphere schema data.
     * 
     * @param databaseName database name
     * @param schemaName schema name
     */
    public synchronized void dropShardingSphereSchemaData(final String databaseName, final String schemaName) {
        ShardingSphereDatabaseData databaseData = metaDataContexts.get().getShardingSphereData().getDatabase(databaseName);
        if (null == databaseData || !databaseData.containsSchema(schemaName)) {
            return;
        }
        databaseData.removeSchema(schemaName);
    }
    
    /**
     * Add ShardingSphere table data.
     * 
     * @param databaseName database name
     * @param schemaName schema name
     * @param tableName table name
     */
    public synchronized void addShardingSphereTableData(final String databaseName, final String schemaName, final String tableName) {
        if (!metaDataContexts.get().getShardingSphereData().containsDatabase(databaseName) || !metaDataContexts.get().getShardingSphereData().getDatabase(databaseName).containsSchema(schemaName)) {
            return;
        }
        if (metaDataContexts.get().getShardingSphereData().getDatabase(databaseName).getSchema(schemaName).containsTable(tableName)) {
            return;
        }
        metaDataContexts.get().getShardingSphereData().getDatabase(databaseName).getSchema(schemaName).putTable(tableName, new ShardingSphereTableData(tableName));
    }
    
    /**
     * Drop ShardingSphere table data.
     * 
     * @param databaseName database name
     * @param schemaName schema name
     * @param tableName table name
     */
    public synchronized void dropShardingSphereTableData(final String databaseName, final String schemaName, final String tableName) {
        if (!metaDataContexts.get().getShardingSphereData().containsDatabase(databaseName) || !metaDataContexts.get().getShardingSphereData().getDatabase(databaseName).containsSchema(schemaName)) {
            return;
        }
        metaDataContexts.get().getShardingSphereData().getDatabase(databaseName).getSchema(schemaName).removeTable(tableName);
    }
    
    /**
     * Alter ShardingSphere row data.
     * 
     * @param databaseName database name
     * @param schemaName schema name
     * @param tableName table name
     * @param yamlRowData yaml row data
     */
    public synchronized void alterShardingSphereRowData(final String databaseName, final String schemaName, final String tableName, final YamlShardingSphereRowData yamlRowData) {
        if (!metaDataContexts.get().getShardingSphereData().containsDatabase(databaseName) || !metaDataContexts.get().getShardingSphereData().getDatabase(databaseName).containsSchema(schemaName)
                || !metaDataContexts.get().getShardingSphereData().getDatabase(databaseName).getSchema(schemaName).containsTable(tableName)) {
            return;
        }
        if (!metaDataContexts.get().getMetaData().containsDatabase(databaseName) || !metaDataContexts.get().getMetaData().getDatabase(databaseName).containsSchema(schemaName)
                || !metaDataContexts.get().getMetaData().getDatabase(databaseName).getSchema(schemaName).containsTable(tableName)) {
            return;
        }
        ShardingSphereTableData tableData = metaDataContexts.get().getShardingSphereData().getDatabase(databaseName).getSchema(schemaName).getTable(tableName);
        List<ShardingSphereColumn> columns = new ArrayList<>(metaDataContexts.get().getMetaData().getDatabase(databaseName).getSchema(schemaName).getTable(tableName).getColumns());
        tableData.getRows().add(new YamlShardingSphereRowDataSwapper(columns).swapToObject(yamlRowData));
    }
    
    /**
     * Delete ShardingSphere row data.
     * 
     * @param databaseName database name
     * @param schemaName schema name
     * @param tableName table name
     * @param uniqueKey row uniqueKey
     */
    public synchronized void deleteShardingSphereRowData(final String databaseName, final String schemaName, final String tableName, final String uniqueKey) {
        if (!metaDataContexts.get().getShardingSphereData().containsDatabase(databaseName) || !metaDataContexts.get().getShardingSphereData().getDatabase(databaseName).containsSchema(schemaName)
                || !metaDataContexts.get().getShardingSphereData().getDatabase(databaseName).getSchema(schemaName).containsTable(tableName)) {
            return;
        }
        metaDataContexts.get().getShardingSphereData().getDatabase(databaseName).getSchema(schemaName).getTable(tableName).getRows().removeIf(each -> uniqueKey.equals(each.getUniqueKey()));
    }
    
    /**
     * Update cluster state.
     * 
     * @param status status
     */
    public void updateClusterState(final String status) {
        try {
            clusterStateContext.switchState(ClusterState.valueOf(status));
        } catch (final IllegalArgumentException ignore) {
        }
    }
    
    @Override
    public void close() {
        executorEngine.close();
        metaDataContexts.get().close();
    }
}
