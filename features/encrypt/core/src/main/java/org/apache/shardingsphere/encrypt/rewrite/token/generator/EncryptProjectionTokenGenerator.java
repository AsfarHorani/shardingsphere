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

package org.apache.shardingsphere.encrypt.rewrite.token.generator;

import com.google.common.base.Preconditions;
import lombok.Setter;
import org.apache.shardingsphere.encrypt.rewrite.aware.EncryptRuleAware;
import org.apache.shardingsphere.encrypt.rule.EncryptRule;
import org.apache.shardingsphere.infra.binder.segment.select.projection.Projection;
import org.apache.shardingsphere.infra.binder.segment.select.projection.ProjectionsContext;
import org.apache.shardingsphere.infra.binder.segment.select.projection.impl.ColumnProjection;
import org.apache.shardingsphere.infra.binder.segment.select.projection.impl.ShorthandProjection;
import org.apache.shardingsphere.infra.binder.statement.SQLStatementContext;
import org.apache.shardingsphere.infra.binder.statement.dml.SelectStatementContext;
import org.apache.shardingsphere.infra.database.type.DatabaseTypeEngine;
import org.apache.shardingsphere.infra.metadata.database.schema.model.ShardingSphereSchema;
import org.apache.shardingsphere.infra.rewrite.sql.token.generator.CollectionSQLTokenGenerator;
import org.apache.shardingsphere.infra.rewrite.sql.token.generator.aware.PreviousSQLTokensAware;
import org.apache.shardingsphere.infra.rewrite.sql.token.generator.aware.SchemaMetaDataAware;
import org.apache.shardingsphere.infra.rewrite.sql.token.pojo.SQLToken;
import org.apache.shardingsphere.infra.rewrite.sql.token.pojo.generic.SubstitutableColumnNameToken;
import org.apache.shardingsphere.sql.parser.sql.common.enums.SubqueryType;
import org.apache.shardingsphere.sql.parser.sql.common.segment.dml.item.ColumnProjectionSegment;
import org.apache.shardingsphere.sql.parser.sql.common.segment.dml.item.ProjectionSegment;
import org.apache.shardingsphere.sql.parser.sql.common.segment.dml.item.ShorthandProjectionSegment;
import org.apache.shardingsphere.sql.parser.sql.common.segment.generic.AliasSegment;
import org.apache.shardingsphere.sql.parser.sql.common.segment.generic.OwnerSegment;
import org.apache.shardingsphere.sql.parser.sql.common.value.identifier.IdentifierValue;

import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Projection token generator for encrypt.
 */
@Setter
public final class EncryptProjectionTokenGenerator implements CollectionSQLTokenGenerator<SQLStatementContext>, PreviousSQLTokensAware, SchemaMetaDataAware, EncryptRuleAware {
    
    private List<SQLToken> previousSQLTokens;
    
    private EncryptRule encryptRule;
    
    private String databaseName;
    
    private Map<String, ShardingSphereSchema> schemas;
    
    @Override
    public boolean isGenerateSQLToken(final SQLStatementContext sqlStatementContext) {
        return sqlStatementContext instanceof SelectStatementContext && !((SelectStatementContext) sqlStatementContext).getAllTables().isEmpty();
    }
    
    @Override
    public Collection<SQLToken> generateSQLTokens(final SQLStatementContext sqlStatementContext) {
        Preconditions.checkState(sqlStatementContext instanceof SelectStatementContext);
        Collection<SQLToken> result = new LinkedHashSet<>();
        SelectStatementContext selectStatementContext = (SelectStatementContext) sqlStatementContext;
        addGenerateSQLTokens(result, selectStatementContext);
        for (SelectStatementContext each : selectStatementContext.getSubqueryContexts().values()) {
            addGenerateSQLTokens(result, each);
        }
        return result;
    }
    
    private void addGenerateSQLTokens(final Collection<SQLToken> result, final SelectStatementContext selectStatementContext) {
        Map<String, String> columnTableNames = getColumnTableNames(selectStatementContext);
        for (ProjectionSegment each : selectStatementContext.getSqlStatement().getProjections().getProjections()) {
            SubqueryType subqueryType = selectStatementContext.getSubqueryType();
            if (each instanceof ColumnProjectionSegment) {
                ColumnProjectionSegment columnSegment = (ColumnProjectionSegment) each;
                ColumnProjection columnProjection = buildColumnProjection(columnSegment);
                String tableName = columnTableNames.get(columnProjection.getExpression());
                if (null != tableName && encryptRule.findEncryptTable(tableName).flatMap(optional -> optional.findEncryptColumn(columnProjection.getName())).isPresent()) {
                    result.add(generateSQLToken(tableName, columnSegment, columnProjection, subqueryType));
                }
            }
            if (each instanceof ShorthandProjectionSegment) {
                ShorthandProjectionSegment shorthandSegment = (ShorthandProjectionSegment) each;
                Collection<Projection> actualColumns = getShorthandProjection(shorthandSegment, selectStatementContext.getProjectionsContext()).getActualColumns();
                if (!actualColumns.isEmpty()) {
                    result.add(generateSQLToken(shorthandSegment, actualColumns, selectStatementContext, subqueryType, columnTableNames));
                }
            }
        }
    }
    
    private boolean isEncryptColumn(final String tableName, final String columnName) {
        return null != tableName && encryptRule.findEncryptTable(tableName).flatMap(optional -> optional.findEncryptColumn(columnName)).isPresent();
    }
    
    private SubstitutableColumnNameToken generateSQLToken(final String tableName, final ColumnProjectionSegment columnSegment,
                                                          final ColumnProjection columnProjection, final SubqueryType subqueryType) {
        Collection<Projection> projections = generateProjections(tableName, columnProjection, subqueryType, false, null);
        int startIndex = columnSegment.getColumn().getOwner().isPresent() ? columnSegment.getColumn().getOwner().get().getStopIndex() + 2 : columnSegment.getColumn().getStartIndex();
        int stopIndex = columnSegment.getStopIndex();
        return new SubstitutableColumnNameToken(startIndex, stopIndex, projections);
    }
    
    private SubstitutableColumnNameToken generateSQLToken(final ShorthandProjectionSegment segment, final Collection<Projection> actualColumns,
                                                          final SelectStatementContext selectStatementContext, final SubqueryType subqueryType, final Map<String, String> columnTableNames) {
        List<Projection> projections = new LinkedList<>();
        for (Projection each : actualColumns) {
            String tableName = columnTableNames.get(each.getExpression());
            if (null == tableName || !encryptRule.findEncryptTable(tableName).flatMap(optional -> optional.findEncryptColumn(each.getColumnLabel())).isPresent()) {
                projections.add(each.getAlias().map(optional -> (Projection) new ColumnProjection(null, optional, null)).orElse(each));
            } else if (each instanceof ColumnProjection) {
                projections.addAll(generateProjections(tableName, (ColumnProjection) each, subqueryType, true, segment));
            }
        }
        int startIndex = segment.getOwner().isPresent() ? segment.getOwner().get().getStartIndex() : segment.getStartIndex();
        previousSQLTokens.removeIf(each -> each.getStartIndex() == startIndex);
        return new SubstitutableColumnNameToken(startIndex, segment.getStopIndex(), projections, selectStatementContext.getDatabaseType().getQuoteCharacter());
    }
    
    private ColumnProjection buildColumnProjection(final ColumnProjectionSegment segment) {
        IdentifierValue owner = segment.getColumn().getOwner().map(OwnerSegment::getIdentifier).orElse(null);
        return new ColumnProjection(owner, segment.getColumn().getIdentifier(), segment.getAliasName().isPresent() ? segment.getAlias().map(AliasSegment::getIdentifier).orElse(null) : null);
    }
    
    private Map<String, String> getColumnTableNames(final SelectStatementContext selectStatementContext) {
        Collection<ColumnProjection> columns = new LinkedList<>();
        for (Projection projection : selectStatementContext.getProjectionsContext().getProjections()) {
            if (projection instanceof ColumnProjection) {
                columns.add((ColumnProjection) projection);
            }
            if (projection instanceof ShorthandProjection) {
                columns.addAll(((ShorthandProjection) projection).getColumnProjections());
            }
        }
        String defaultSchema = DatabaseTypeEngine.getDefaultSchemaName(selectStatementContext.getDatabaseType(), databaseName);
        ShardingSphereSchema schema = selectStatementContext.getTablesContext().getSchemaName().map(schemas::get).orElseGet(() -> schemas.get(defaultSchema));
        return selectStatementContext.getTablesContext().findTableNamesByColumnProjection(columns, schema);
    }
    
    private Collection<Projection> generateProjections(final String tableName, final ColumnProjection column, final SubqueryType subqueryType, final boolean shorthand,
                                                       final ShorthandProjectionSegment segment) {
        Collection<Projection> result = new LinkedList<>();
        if (SubqueryType.PREDICATE_SUBQUERY == subqueryType) {
            result.add(distinctOwner(generatePredicateSubqueryProjection(tableName, column), shorthand));
        } else if (SubqueryType.TABLE_SUBQUERY == subqueryType) {
            result.addAll(generateTableSubqueryProjections(tableName, column, shorthand));
        } else if (SubqueryType.EXISTS_SUBQUERY == subqueryType) {
            result.addAll(generateExistsSubqueryProjections(tableName, column, shorthand));
        } else {
            result.add(distinctOwner(generateCommonProjection(tableName, column, segment), shorthand));
        }
        return result;
    }
    
    private ColumnProjection distinctOwner(final ColumnProjection column, final boolean shorthand) {
        if (shorthand || null == column.getOwner()) {
            return column;
        }
        return new ColumnProjection(null, column.getNameIdentifier(), column.getAlias().isPresent() ? column.getAliasIdentifier() : null);
    }
    
    private ColumnProjection generatePredicateSubqueryProjection(final String tableName, final ColumnProjection column) {
        Optional<String> assistedQueryColumn = encryptRule.findAssistedQueryColumn(tableName, column.getName());
        if (assistedQueryColumn.isPresent()) {
            return new ColumnProjection(column.getOwnerIdentifier(), new IdentifierValue(assistedQueryColumn.get(), column.getNameIdentifier().getQuoteCharacter()), null);
        }
        String cipherColumn = encryptRule.getCipherColumn(tableName, column.getName());
        return new ColumnProjection(column.getOwnerIdentifier(), new IdentifierValue(cipherColumn, column.getNameIdentifier().getQuoteCharacter()), null);
    }
    
    private Collection<ColumnProjection> generateTableSubqueryProjections(final String tableName, final ColumnProjection column, final boolean shorthand) {
        Collection<ColumnProjection> result = new LinkedList<>();
        result.add(distinctOwner(new ColumnProjection(column.getOwnerIdentifier(), new IdentifierValue(encryptRule.getCipherColumn(tableName, column.getName()),
                column.getNameIdentifier().getQuoteCharacter()), null), shorthand));
        Optional<String> assistedQueryColumn = encryptRule.findAssistedQueryColumn(tableName, column.getName());
        assistedQueryColumn.ifPresent(optional -> result.add(new ColumnProjection(column.getOwnerIdentifier(), new IdentifierValue(optional, column.getNameIdentifier().getQuoteCharacter()), null)));
        return result;
    }
    
    private Collection<ColumnProjection> generateExistsSubqueryProjections(final String tableName, final ColumnProjection column, final boolean shorthand) {
        Collection<ColumnProjection> result = new LinkedList<>();
        result.add(distinctOwner(new ColumnProjection(column.getOwnerIdentifier(), new IdentifierValue(encryptRule.getCipherColumn(tableName, column.getName()),
                column.getNameIdentifier().getQuoteCharacter()), null), shorthand));
        return result;
    }
    
    private ColumnProjection generateCommonProjection(final String tableName, final ColumnProjection column, final ShorthandProjectionSegment segment) {
        String encryptColumnName = getEncryptColumnName(tableName, column.getName());
        IdentifierValue owner = (null == segment || !segment.getOwner().isPresent()) ? column.getOwnerIdentifier() : segment.getOwner().get().getIdentifier();
        return new ColumnProjection(owner, new IdentifierValue(encryptColumnName, column.getNameIdentifier().getQuoteCharacter()), column.getAlias().isPresent()
                ? column.getAliasIdentifier()
                : column.getNameIdentifier());
    }
    
    private String getEncryptColumnName(final String tableName, final String logicEncryptColumnName) {
        return encryptRule.getCipherColumn(tableName, logicEncryptColumnName);
    }
    
    private ShorthandProjection getShorthandProjection(final ShorthandProjectionSegment segment, final ProjectionsContext projectionsContext) {
        Optional<String> owner = segment.getOwner().isPresent() ? Optional.of(segment.getOwner().get().getIdentifier().getValue()) : Optional.empty();
        for (Projection each : projectionsContext.getProjections()) {
            if (each instanceof ShorthandProjection) {
                if (!owner.isPresent() && !((ShorthandProjection) each).getOwner().isPresent()) {
                    return (ShorthandProjection) each;
                }
                if (owner.isPresent() && owner.get().equals(((ShorthandProjection) each).getOwner().orElse(null))) {
                    return (ShorthandProjection) each;
                }
            }
        }
        throw new IllegalStateException(String.format("Can not find shorthand projection segment, owner is `%s`", owner.orElse(null)));
    }
}
