/*
 * Copyright 2016 Red Hat, Inc. and/or its affiliates.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *       http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.drools.workbench.screens.dtablexls.backend.server.conversion;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

import org.drools.workbench.models.commons.backend.rule.RuleModelDRLPersistenceImpl;
import org.drools.workbench.models.datamodel.oracle.DataType;
import org.drools.workbench.models.datamodel.oracle.PackageDataModelOracle;
import org.drools.workbench.models.datamodel.rule.IAction;
import org.drools.workbench.models.datamodel.rule.IPattern;
import org.drools.workbench.models.datamodel.rule.InterpolationVariable;
import org.drools.workbench.models.datamodel.rule.RuleModel;
import org.drools.workbench.models.datamodel.rule.visitors.RuleModelVisitor;
import org.drools.workbench.models.guided.dtable.shared.conversion.ConversionMessageType;
import org.drools.workbench.models.guided.dtable.shared.conversion.ConversionResult;
import org.drools.workbench.models.guided.dtable.shared.model.BRLActionColumn;
import org.drools.workbench.models.guided.dtable.shared.model.BRLActionVariableColumn;
import org.drools.workbench.models.guided.dtable.shared.model.BRLColumn;
import org.drools.workbench.models.guided.dtable.shared.model.BRLConditionColumn;
import org.drools.workbench.models.guided.dtable.shared.model.BRLConditionVariableColumn;
import org.drools.workbench.models.guided.dtable.shared.model.BRLVariableColumn;
import org.drools.workbench.models.guided.dtable.shared.model.BaseColumn;
import org.drools.workbench.models.guided.dtable.shared.model.DTCellValue52;
import org.drools.workbench.models.guided.dtable.shared.model.GuidedDecisionTable52;
import org.drools.workbench.screens.dtablexls.backend.server.conversion.builders.GuidedDecisionTableLHSBuilder;
import org.drools.workbench.screens.dtablexls.backend.server.conversion.builders.GuidedDecisionTableRHSBuilder;
import org.drools.workbench.screens.dtablexls.backend.server.conversion.builders.GuidedDecisionTableSourceBuilder;
import org.drools.workbench.screens.dtablexls.backend.server.conversion.builders.GuidedDecisionTableSourceBuilderDirect;
import org.drools.workbench.screens.dtablexls.backend.server.conversion.builders.GuidedDecisionTableSourceBuilderIndirect;
import org.drools.workbench.screens.dtablexls.backend.server.conversion.builders.LiteralValueBuilder;
import org.drools.workbench.screens.dtablexls.backend.server.conversion.builders.ParameterizedValueBuilder;

import static org.drools.workbench.screens.dtablexls.backend.server.conversion.DTCellValueUtilities.*;

public class GuidedDecisionTablePopulater {

    private static final String UNDEFINED = "(undefined)";

    private final GuidedDecisionTable52 dtable;
    private final List<GuidedDecisionTableSourceBuilder> sourceBuilders;
    private final ConversionResult conversionResult;
    private final PackageDataModelOracle dmo;
    private final int ruleRowStartIndex;
    private final int ruleColumnStartIndex;

    public GuidedDecisionTablePopulater( final GuidedDecisionTable52 dtable,
                                         final List<GuidedDecisionTableSourceBuilder> sourceBuilders,
                                         final ConversionResult conversionResult,
                                         final PackageDataModelOracle dmo,
                                         final int ruleRowStartIndex,
                                         final int ruleColumnStartIndex ) {
        this.dtable = dtable;
        this.sourceBuilders = sourceBuilders;
        this.conversionResult = conversionResult;
        this.dmo = dmo;
        this.ruleRowStartIndex = ruleRowStartIndex;
        this.ruleColumnStartIndex = ruleColumnStartIndex;
    }

    public void populate() {
        final int maxRowCount = getMaxRowCount();
        processDirectSourceBuilders( maxRowCount );
        processIndirectSourceBuilders( maxRowCount );
    }

    //Get maximum row count from all SourceBuilders
    private int getMaxRowCount() {
        int maxRowCount = 0;
        for ( GuidedDecisionTableSourceBuilder sb : sourceBuilders ) {
            maxRowCount = Math.max( maxRowCount,
                                    sb.getRowCount() );
        }
        return maxRowCount;
    }

    //Direct SourceBuilders append data and columns to dtable
    private void processDirectSourceBuilders( final int maxRowCount ) {
        for ( GuidedDecisionTableSourceBuilder sb : sourceBuilders ) {
            if ( sb instanceof GuidedDecisionTableSourceBuilderDirect ) {
                ( (GuidedDecisionTableSourceBuilderDirect) sb ).populateDecisionTable( dtable,
                                                                                       maxRowCount );
            }
        }
    }

    //Indirect SourceBuilders need additional support to append data and columns to dtable
    private void processIndirectSourceBuilders( final int maxRowCount ) {
        addIndirectSourceBuildersColumns();
        addIndirectSourceBuildersData( maxRowCount );
    }

    private void addIndirectSourceBuildersColumns() {
        final List<BRLVariableColumn> variableColumns = new ArrayList<BRLVariableColumn>();
        for ( GuidedDecisionTableSourceBuilder sb : sourceBuilders ) {
            if ( sb instanceof GuidedDecisionTableSourceBuilderIndirect ) {
                for ( BRLVariableColumn variableColumn : ( (GuidedDecisionTableSourceBuilderIndirect) sb ).getVariableColumns() ) {
                    variableColumns.add( variableColumn );
                }
            }
        }

        //Convert the DRL to a RuleModel from which we construct BRLFragment columns
        final StringBuilder rule = new StringBuilder();
        if ( !( dmo.getPackageName() == null || dmo.getPackageName().isEmpty() ) ) {
            rule.append( "package " ).append( dmo.getPackageName() ).append( "\n" );
        }
        rule.append( "rule 'temp' \n" ).append( "when \n" );
        for ( GuidedDecisionTableSourceBuilder sb : sourceBuilders ) {
            if ( sb instanceof GuidedDecisionTableLHSBuilder ) {
                rule.append( sb.getResult() );
            }
        }
        rule.append( "\nthen \n" );
        for ( GuidedDecisionTableSourceBuilder sb : sourceBuilders ) {
            if ( sb instanceof GuidedDecisionTableRHSBuilder ) {
                rule.append( sb.getResult() );
            }
        }
        rule.append( "end" );
        final RuleModel rm = RuleModelDRLPersistenceImpl.getInstance().unmarshal( rule.toString(),
                                                                                  Collections.emptyList(),
                                                                                  dmo );
        if ( rm.lhs != null ) {
            for ( IPattern pattern : rm.lhs ) {
                final BRLConditionColumn column = new BRLConditionColumn();
                column.getDefinition().add( pattern );
                dtable.getConditions().add( column );

                final Map<InterpolationVariable, Integer> templateKeys = new HashMap<>();
                final RuleModelVisitor rmv = new RuleModelVisitor( templateKeys );
                rmv.visit( pattern );

                final List<InterpolationVariable> ivs = new ArrayList<>( templateKeys.keySet() );
                for ( BRLVariableColumn variableColumn : variableColumns ) {
                    final Iterator<InterpolationVariable> ivsIts = ivs.iterator();
                    while ( ivsIts.hasNext() ) {
                        final InterpolationVariable iv = ivsIts.next();
                        if ( iv.getVarName().equals( variableColumn.getVarName() ) ) {
                            final BRLConditionVariableColumn source = (BRLConditionVariableColumn) variableColumn;
                            final BRLConditionVariableColumn target = makeBRLConditionVariableColumn( source,
                                                                                                      iv );
                            column.getChildColumns().add( target );
                            ivsIts.remove();
                        }
                    }
                }

                if ( column.getChildColumns().isEmpty() ) {
                    setZeroParameterConditionColumnHeader( column,
                                                           variableColumns );
                } else {
                    setCompositeColumnHeader( column );
                }
            }
        }

        if ( rm.rhs != null ) {
            for ( IAction action : rm.rhs ) {
                final BRLActionColumn column = new BRLActionColumn();
                column.getDefinition().add( action );
                dtable.getActionCols().add( column );

                final Map<InterpolationVariable, Integer> templateKeys = new HashMap<>();
                final RuleModelVisitor rmv = new RuleModelVisitor( rm.lhs,
                                                                   templateKeys );
                rmv.visit( action );

                final List<InterpolationVariable> ivs = new ArrayList<>( templateKeys.keySet() );
                for ( BRLVariableColumn variableColumn : variableColumns ) {
                    final Iterator<InterpolationVariable> ivsIts = ivs.iterator();
                    while ( ivsIts.hasNext() ) {
                        final InterpolationVariable iv = ivsIts.next();
                        if ( iv.getVarName().equals( variableColumn.getVarName() ) ) {
                            final BRLActionVariableColumn source = (BRLActionVariableColumn) variableColumn;
                            final BRLActionVariableColumn target = makeBRLActionVariableColumn( source,
                                                                                                iv );
                            column.getChildColumns().add( target );
                            ivsIts.remove();
                        }
                    }
                }

                if ( column.getChildColumns().isEmpty() ) {
                    setZeroParameterActionColumnHeader( column,
                                                        variableColumns );
                } else {
                    setCompositeColumnHeader( column );
                }

            }
        }
    }

    private BRLConditionVariableColumn makeBRLConditionVariableColumn( final BRLConditionVariableColumn source,
                                                                       final InterpolationVariable iv ) {
        final String varName = source.getVarName();
        final String dataType = iv.getDataType() == null ? DataType.TYPE_OBJECT : iv.getDataType();
        final String factType = iv.getFactType();
        final String factField = iv.getFactField();

        BRLConditionVariableColumn target;
        if ( factType != null && factField != null ) {
            target = new BRLConditionVariableColumn( varName,
                                                     dataType,
                                                     factType,
                                                     factField );
        } else {
            target = new BRLConditionVariableColumn( varName,
                                                     dataType );

        }
        target.setHeader( source.getHeader() );

        return target;
    }

    private void setZeroParameterConditionColumnHeader( final BRLConditionColumn column,
                                                        final List<BRLVariableColumn> allVariableColumns ) {
        final BRLConditionVariableColumn source = findZeroParameterSourceConditionColumn( allVariableColumns );
        final BRLConditionVariableColumn target = new BRLConditionVariableColumn( "",
                                                                                  DataType.TYPE_BOOLEAN );
        column.getChildColumns().add( target );

        setZeroParameterColumnHeader( column,
                                      source,
                                      target );
    }

    private BRLConditionVariableColumn findZeroParameterSourceConditionColumn( final List<BRLVariableColumn> variableColumns ) {
        for ( BRLVariableColumn variableColumn : variableColumns ) {
            if ( variableColumn instanceof BRLConditionVariableColumn ) {
                if ( variableColumn.getVarName().equals( "" ) ) {
                    return (BRLConditionVariableColumn) variableColumn;
                }
            }
        }
        return null;
    }

    private void setCompositeColumnHeader( final BRLConditionColumn column ) {
        final List<BRLConditionVariableColumn> columnVariableColumns = column.getChildColumns();
        final StringBuilder sb = new StringBuilder();
        final List<String> variableColumnHeaders = new ArrayList<>();
        sb.append( "Converted from [" );
        for ( int i = 0; i < columnVariableColumns.size(); i++ ) {
            final BRLConditionVariableColumn variableColumn = columnVariableColumns.get( i );
            final String header = variableColumn.getHeader();
            variableColumnHeaders.add( header );
            sb.append( "'" ).append( header ).append( "'" );
            sb.append( i < columnVariableColumns.size() - 1 ? ", " : "" );
        }
        sb.append( "]" );

        column.setHeader( sb.toString() );

        for ( int i = 0; i < columnVariableColumns.size(); i++ ) {
            final BRLConditionVariableColumn variableColumn = columnVariableColumns.get( i );
            variableColumn.setHeader( variableColumnHeaders.get( i ) );
        }
    }

    private BRLActionVariableColumn makeBRLActionVariableColumn( final BRLActionVariableColumn source,
                                                                 final InterpolationVariable iv ) {
        final String varName = source.getVarName();
        final String dataType = iv.getDataType() == null ? DataType.TYPE_OBJECT : iv.getDataType();
        final String factType = iv.getFactType();
        final String factField = iv.getFactField();

        BRLActionVariableColumn target;
        if ( factType != null && factField != null ) {
            target = new BRLActionVariableColumn( varName,
                                                  dataType,
                                                  factType,
                                                  factField );
        } else {
            target = new BRLActionVariableColumn( varName,
                                                  dataType );

        }
        target.setHeader( source.getHeader() );

        return target;
    }

    private void setZeroParameterActionColumnHeader( final BRLActionColumn column,
                                                     final List<BRLVariableColumn> allVariableColumns ) {
        final BRLActionVariableColumn source = findZeroParameterSourceActionColumn( allVariableColumns );
        final BRLActionVariableColumn target = new BRLActionVariableColumn( "",
                                                                            DataType.TYPE_BOOLEAN );
        column.getChildColumns().add( target );

        setZeroParameterColumnHeader( column,
                                      source,
                                      target );
    }

    private void setCompositeColumnHeader( final BRLActionColumn column ) {
        final List<BRLActionVariableColumn> columnVariableColumns = column.getChildColumns();
        final StringBuilder sb = new StringBuilder();
        final List<String> variableColumnHeaders = new ArrayList<>();
        sb.append( "Converted from [" );
        for ( int i = 0; i < columnVariableColumns.size(); i++ ) {
            final BRLActionVariableColumn variableColumn = columnVariableColumns.get( i );
            final String header = variableColumn.getHeader();
            variableColumnHeaders.add( header );
            sb.append( "'" ).append( header ).append( "'" );
            sb.append( i < columnVariableColumns.size() - 1 ? ", " : "" );
        }
        sb.append( "]" );

        column.setHeader( sb.toString() );

        for ( int i = 0; i < columnVariableColumns.size(); i++ ) {
            final BRLActionVariableColumn variableColumn = columnVariableColumns.get( i );
            variableColumn.setHeader( variableColumnHeaders.get( i ) );
        }
    }

    private BRLActionVariableColumn findZeroParameterSourceActionColumn( final List<BRLVariableColumn> variableColumns ) {
        for ( BRLVariableColumn variableColumn : variableColumns ) {
            if ( variableColumn instanceof BRLActionVariableColumn ) {
                if ( variableColumn.getVarName().equals( "" ) ) {
                    return (BRLActionVariableColumn) variableColumn;
                }
            }
        }
        return null;
    }

    private void setZeroParameterColumnHeader( final BRLColumn column,
                                               final BaseColumn source,
                                               final BaseColumn target ) {
        final StringBuilder sb = new StringBuilder();
        final String header = source == null ? UNDEFINED : source.getHeader();
        sb.append( source == null ? "" : "Converted from ['" );
        sb.append( header );
        sb.append( source == null ? "" : "']" );
        column.setHeader( sb.toString() );
        target.setHeader( header );
    }

    private void addIndirectSourceBuildersData( final int maxRowCount ) {
        //Get ordered list of ParameterizedValueBuilder for all GuidedDecisionTableSourceBuilderIndirect instances
        //An ordered list of ParameterizedValueBuilder guarantees they are checked in the same order as columns
        //were added to the Guided Decision Table.
        final List<ParameterizedValueBuilder> valueBuilders = new ArrayList<ParameterizedValueBuilder>();
        for ( GuidedDecisionTableSourceBuilder sb : sourceBuilders ) {
            if ( sb instanceof GuidedDecisionTableSourceBuilderIndirect ) {
                final GuidedDecisionTableSourceBuilderIndirect isb = (GuidedDecisionTableSourceBuilderIndirect) sb;
                final Set<Integer> sortedIndexes = new TreeSet<Integer>( isb.getValueBuilders().keySet() );
                for ( Integer index : sortedIndexes ) {
                    final ParameterizedValueBuilder vb = isb.getValueBuilders().get( index );
                    assertFragmentData( vb,
                                        maxRowCount );
                    valueBuilders.add( vb );
                }
            }
        }

        final List<BaseColumn> allColumns = dtable.getExpandedColumns();
        for ( int iColIndex = 0; iColIndex < allColumns.size(); iColIndex++ ) {
            final BaseColumn column = allColumns.get( iColIndex );
            if ( column instanceof BRLVariableColumn ) {
                final String varName = ( (BRLVariableColumn) column ).getVarName();
                final String varDataType = ( (BRLVariableColumn) column ).getFieldType();
                assertDecisionTableData( varName,
                                         varDataType,
                                         valueBuilders,
                                         maxRowCount );
            }
        }
    }

    private List<List<DTCellValue52>> assertFragmentData( final ParameterizedValueBuilder pvb,
                                                          final int maxRowCount ) {
        final List<List<DTCellValue52>> columnData = pvb.getColumnData();
        final List<String> parameters = pvb.getParameters();
        if ( columnData.size() < maxRowCount ) {
            for ( int iRow = columnData.size(); iRow < maxRowCount; iRow++ ) {
                final List<DTCellValue52> brlFragmentData = new ArrayList<DTCellValue52>();
                for ( int iCol = 0; iCol < parameters.size(); iCol++ ) {
                    brlFragmentData.add( new DTCellValue52() );
                }
                columnData.add( brlFragmentData );
            }
        }
        return columnData;
    }

    private void assertDecisionTableData( final String varName,
                                          final String varDataType,
                                          final List<ParameterizedValueBuilder> valueBuilders,
                                          final int maxRowCount ) {
        if ( varName.equals( "" ) ) {
            for ( ParameterizedValueBuilder pvb : valueBuilders ) {
                if ( pvb instanceof LiteralValueBuilder ) {
                    for ( int iRowIndex = 0; iRowIndex < maxRowCount; iRowIndex++ ) {
                        final int _rowIndex = ruleRowStartIndex + iRowIndex;
                        final int _columnIndex = ruleColumnStartIndex + valueBuilders.indexOf( pvb ) + 1;
                        final List<DTCellValue52> fragmentRow = pvb.getColumnData().get( iRowIndex );
                        final List<DTCellValue52> dtableRow = dtable.getData().get( iRowIndex );
                        final DTCellValue52 fragmentCell = fragmentRow.get( 0 );
                        assertDTCellValue( varDataType,
                                           fragmentCell,
                                           ( final String value,
                                             final DataType.DataTypes dataType ) -> addConversionMessage( value,
                                                                                                          dataType,
                                                                                                          _rowIndex,
                                                                                                          _columnIndex ) );
                        dtableRow.add( fragmentCell );
                    }
                    break;
                }
            }

        } else {
            for ( ParameterizedValueBuilder pvb : valueBuilders ) {
                final int varNameIndex = pvb.getParameters().indexOf( varName );
                if ( varNameIndex > -1 ) {
                    for ( int iRowIndex = 0; iRowIndex < maxRowCount; iRowIndex++ ) {
                        final int _rowIndex = ruleRowStartIndex + iRowIndex;
                        final int _columnIndex = ruleColumnStartIndex + valueBuilders.indexOf( pvb ) + 1;
                        final List<DTCellValue52> fragmentRow = pvb.getColumnData().get( iRowIndex );
                        final List<DTCellValue52> dtableRow = dtable.getData().get( iRowIndex );
                        final DTCellValue52 fragmentCell = fragmentRow.get( varNameIndex );
                        assertDTCellValue( varDataType,
                                           fragmentCell,
                                           ( final String value,
                                             final DataType.DataTypes dataType ) -> addConversionMessage( value,
                                                                                                          dataType,
                                                                                                          _rowIndex,
                                                                                                          _columnIndex ) );
                        dtableRow.add( fragmentCell );
                    }
                    break;
                }
            }
        }
    }

    private void addConversionMessage( final String value,
                                       final DataType.DataTypes dataType,
                                       final int rowIndex,
                                       final int columnIndex ) {
        conversionResult.addMessage( "Unable to convert value '" + value + "' to " + dataType + ". Cell (" + toColumnName( columnIndex ) + rowIndex + ")",
                                     ConversionMessageType.WARNING );
    }

    private String toColumnName( int columnIndex ) {
        StringBuilder sb = new StringBuilder();
        while ( columnIndex-- > 0 ) {
            sb.append( (char) ( 'A' + ( columnIndex % 26 ) ) );
            columnIndex /= 26;
        }
        return sb.reverse().toString();
    }

}
