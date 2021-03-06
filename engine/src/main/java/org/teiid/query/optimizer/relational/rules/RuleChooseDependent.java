/*
 * JBoss, Home of Professional Open Source.
 * See the COPYRIGHT.txt file distributed with this work for information
 * regarding copyright ownership.  Some portions may be licensed
 * to Red Hat, Inc. under one or more contributor license agreements.
 * 
 * This library is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Lesser General Public
 * License as published by the Free Software Foundation; either
 * version 2.1 of the License, or (at your option) any later version.
 * 
 * This library is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 * Lesser General Public License for more details.
 * 
 * You should have received a copy of the GNU Lesser General Public
 * License along with this library; if not, write to the Free Software
 * Foundation, Inc., 51 Franklin Street, Fifth Floor, Boston, MA
 * 02110-1301 USA.
 */

package org.teiid.query.optimizer.relational.rules;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Iterator;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import org.teiid.api.exception.query.QueryMetadataException;
import org.teiid.api.exception.query.QueryPlannerException;
import org.teiid.common.buffer.BufferManager;
import org.teiid.core.TeiidComponentException;
import org.teiid.core.types.DataTypeManager.DefaultDataClasses;
import org.teiid.query.analysis.AnalysisRecord;
import org.teiid.query.metadata.QueryMetadataInterface;
import org.teiid.query.optimizer.capabilities.CapabilitiesFinder;
import org.teiid.query.optimizer.relational.OptimizerRule;
import org.teiid.query.optimizer.relational.RelationalPlanner;
import org.teiid.query.optimizer.relational.RuleStack;
import org.teiid.query.optimizer.relational.plantree.NodeConstants;
import org.teiid.query.optimizer.relational.plantree.NodeEditor;
import org.teiid.query.optimizer.relational.plantree.PlanNode;
import org.teiid.query.optimizer.relational.rules.NewCalculateCostUtil.DependentCostAnalysis;
import org.teiid.query.sql.lang.DependentSetCriteria;
import org.teiid.query.sql.lang.DependentSetCriteria.AttributeComparison;
import org.teiid.query.sql.lang.JoinType;
import org.teiid.query.sql.symbol.Array;
import org.teiid.query.sql.symbol.ElementSymbol;
import org.teiid.query.sql.symbol.Expression;
import org.teiid.query.sql.symbol.GroupSymbol;
import org.teiid.query.sql.util.SymbolMap;
import org.teiid.query.sql.visitor.ElementCollectorVisitor;
import org.teiid.query.util.CommandContext;


/**
 * Finds nodes that can be turned into dependent joins 
 */
public final class RuleChooseDependent implements OptimizerRule {
	
	private static AtomicInteger ID = new AtomicInteger();

    private static class CandidateJoin {
        PlanNode joinNode;
        boolean leftCandidate;
        boolean rightCandidate;
    }

	public static final int DEFAULT_INDEPENDENT_CARDINALITY = 10;
	public static final int UNKNOWN_INDEPENDENT_CARDINALITY = BufferManager.DEFAULT_PROCESSOR_BATCH_SIZE;
    
    public PlanNode execute(PlanNode plan, QueryMetadataInterface metadata, CapabilitiesFinder capFinder, RuleStack rules, AnalysisRecord analysisRecord, CommandContext context)
        throws QueryPlannerException, QueryMetadataException, TeiidComponentException {
        
        // Find first criteria node in plan with conjuncts        
        List<CandidateJoin> matches = findCandidate(plan, metadata, analysisRecord);
        
        boolean pushCriteria = false;

        // Handle all cases where both siblings are possible matches
        for (CandidateJoin entry : matches) {
            PlanNode joinNode = entry.joinNode;
            
            PlanNode sourceNode = entry.leftCandidate?joinNode.getFirstChild():joinNode.getLastChild();
            
            PlanNode siblingNode = entry.leftCandidate?joinNode.getLastChild():joinNode.getFirstChild();
            
            boolean bothCandidates = entry.leftCandidate&&entry.rightCandidate;
            
            PlanNode chosenNode = chooseDepWithoutCosting(sourceNode, bothCandidates?siblingNode:null, analysisRecord);
            if(chosenNode != null) {
                pushCriteria |= markDependent(chosenNode, joinNode, metadata, null, false);
                continue;
            }   
            
            DependentCostAnalysis dca = NewCalculateCostUtil.computeCostForDepJoin(joinNode, !entry.leftCandidate, metadata, capFinder, context);
            PlanNode dependentNode = sourceNode;
            
            if (bothCandidates && dca.expectedCardinality == null) {
                dca = NewCalculateCostUtil.computeCostForDepJoin(joinNode, true, metadata, capFinder, context);
                if (dca.expectedCardinality != null) {
                    dependentNode = siblingNode;
                }
            }
            
            if (dca.expectedCardinality != null) {
                pushCriteria |= markDependent(dependentNode, joinNode, metadata, dca, null);
            } else {
            	float sourceCost = NewCalculateCostUtil.computeCostForTree(sourceNode, metadata);
            	float siblingCost = NewCalculateCostUtil.computeCostForTree(siblingNode, metadata);
            	
                if (bothCandidates && sourceCost != NewCalculateCostUtil.UNKNOWN_VALUE && ((sourceCost <= RuleChooseDependent.DEFAULT_INDEPENDENT_CARDINALITY 
                		&& sourceCost <= siblingCost) || (siblingCost == NewCalculateCostUtil.UNKNOWN_VALUE && sourceCost <= UNKNOWN_INDEPENDENT_CARDINALITY))) {
                    pushCriteria |= markDependent(siblingNode, joinNode, metadata, null, sourceCost > RuleChooseDependent.DEFAULT_INDEPENDENT_CARDINALITY?true:null);
                } else if (siblingCost != NewCalculateCostUtil.UNKNOWN_VALUE && (siblingCost <= RuleChooseDependent.DEFAULT_INDEPENDENT_CARDINALITY || (sourceCost == NewCalculateCostUtil.UNKNOWN_VALUE && siblingCost <= UNKNOWN_INDEPENDENT_CARDINALITY))) {
                    pushCriteria |= markDependent(sourceNode, joinNode, metadata, null, siblingCost > RuleChooseDependent.DEFAULT_INDEPENDENT_CARDINALITY?true:null);
                }
            }
        }
        
        if (pushCriteria) {
            // Insert new rules to push down the SELECT criteria
            rules.push(RuleConstants.CLEAN_CRITERIA); //it's important to run clean criteria here since it will remove unnecessary dependent sets
            rules.push(RuleConstants.PUSH_SELECT_CRITERIA);
        }
        
        return plan;
    }    

    /**
     * Walk the tree pre-order, finding all access nodes that are candidates and
     * adding them to the matches list.
     * @param metadata Metadata implementation
     * @param node Root node to search
     * @param matches Collection to accumulate matches in
     * @throws TeiidComponentException 
     * @throws QueryMetadataException 
     */
    List<CandidateJoin> findCandidate(PlanNode root, QueryMetadataInterface metadata, AnalysisRecord analysisRecord) throws QueryMetadataException, TeiidComponentException {

        List<CandidateJoin> candidates = new ArrayList<CandidateJoin>();
        
        for (PlanNode joinNode : NodeEditor.findAllNodes(root, NodeConstants.Types.JOIN, NodeConstants.Types.ACCESS)) {
            CandidateJoin candidate = null;
            
            for (Iterator<PlanNode> j = joinNode.getChildren().iterator(); j.hasNext();) {
                PlanNode child = j.next();
                child = FrameUtil.findJoinSourceNode(child);
                
                if(child.hasBooleanProperty(NodeConstants.Info.MAKE_NOT_DEP) || !isValidJoin(joinNode, child, analysisRecord)) {
                	continue;
                }
                if (candidate == null) {
                    candidate = new CandidateJoin();
                    candidate.joinNode = joinNode;
                    candidates.add(candidate);
                }
                if (j.hasNext()) {
                    candidate.leftCandidate=true;
                } else {
                    candidate.rightCandidate=true;
                }
            }
        }
        
        return candidates;
        
    }
    
    /**
     * Check whether a join is valid.  Invalid joins are CROSS JOIN, FULL OUTER JOIN,
     * any join without criteria, any join with no equality criteria, and any outer 
     * join that has the outer side not the same as the dependent.
     * @param joinNode The join node to check
     * @param sourceNode The access node being considered
     * @param analysisRecord TODO
     * @return True if valid for making dependent
     * @throws TeiidComponentException 
     * @throws QueryMetadataException 
     */
    boolean isValidJoin(PlanNode joinNode, PlanNode sourceNode, AnalysisRecord analysisRecord) throws QueryMetadataException, TeiidComponentException {
        JoinType jtype = (JoinType) joinNode.getProperty(NodeConstants.Info.JOIN_TYPE);

        // Check that join is not a CROSS join or FULL OUTER join
        if(jtype.equals(JoinType.JOIN_CROSS) || jtype.equals(JoinType.JOIN_FULL_OUTER)) {
        	sourceNode.recordDebugAnnotation("parent join is CROSS or FULL OUTER", null, "Rejecting dependent join", analysisRecord, null); //$NON-NLS-1$ //$NON-NLS-2$
            return false;
        }
        
        if (!joinNode.getExportedCorrelatedReferences().isEmpty()) {
        	sourceNode.recordDebugAnnotation("parent join has a correlated nested table", null, "Rejecting dependent join", analysisRecord, null); //$NON-NLS-1$ //$NON-NLS-2$
        	return false;
        }

        // Check that join criteria exist
        List jcrit = (List) joinNode.getProperty(NodeConstants.Info.JOIN_CRITERIA);
        if(jcrit == null || jcrit.size() == 0) {
        	sourceNode.recordDebugAnnotation("parent join has has no join criteria", null, "Rejecting dependent join", analysisRecord, null); //$NON-NLS-1$ //$NON-NLS-2$
            return false;
        }
        
        if(joinNode.getProperty(NodeConstants.Info.LEFT_EXPRESSIONS) == null) { 
        	sourceNode.recordDebugAnnotation("parent join has no equa-join predicates", null, "Rejecting dependent join", analysisRecord, null); //$NON-NLS-1$ //$NON-NLS-2$
            return false;
        }
                        
        // Check that for a left or right outer join the dependent side must be the inner 
        if(jtype.isOuter() && JoinUtil.getInnerSideJoinNodes(joinNode)[0] != sourceNode) {
        	sourceNode.recordDebugAnnotation("node is on outer side of the join", null, "Rejecting dependent join", analysisRecord, null); //$NON-NLS-1$ //$NON-NLS-2$
            return false;
        }

        return true;        
    }
    
    PlanNode chooseDepWithoutCosting(PlanNode rootNode1, PlanNode rootNode2, AnalysisRecord analysisRecord) throws QueryMetadataException, TeiidComponentException  {
    	PlanNode sourceNode1 = FrameUtil.findJoinSourceNode(rootNode1);
        PlanNode sourceNode2 = null;
        
        if (rootNode2 != null) {
            sourceNode2 = FrameUtil.findJoinSourceNode(rootNode2);
        }
        if(sourceNode1.hasCollectionProperty(NodeConstants.Info.ACCESS_PATTERNS) ) {
            if (sourceNode2 != null && sourceNode2.hasCollectionProperty(NodeConstants.Info.ACCESS_PATTERNS) ) {
                //Return null - query planning should fail because both access nodes
                //have unsatisfied access patterns
            	rootNode1.getParent().recordDebugAnnotation("both children have unsatisfied access patterns", null, "Neither node can be made dependent", analysisRecord, null); //$NON-NLS-1$ //$NON-NLS-2$
                return null;
            }  
            rootNode1.recordDebugAnnotation("unsatisfied access pattern detected", null, "marking as dependent side of join", analysisRecord, null); //$NON-NLS-1$ //$NON-NLS-2$
            return rootNode1;
        } else if (sourceNode2 != null && sourceNode2.hasCollectionProperty(NodeConstants.Info.ACCESS_PATTERNS) ) {
            //Access node 2 has unsatisfied access pattern,
            //so try to make node 2 dependent
        	sourceNode2.recordDebugAnnotation("unsatisfied access pattern detected", null, "marking as dependent side of join", analysisRecord, null); //$NON-NLS-1$ //$NON-NLS-2$
            return rootNode2;
        } 
        
        // Check for hints, which over-rule heuristics
        if(sourceNode1.hasBooleanProperty(NodeConstants.Info.MAKE_DEP)) {
        	sourceNode1.recordDebugAnnotation("MAKE_DEP hint detected", null, "marking as dependent side of join", analysisRecord, null); //$NON-NLS-1$ //$NON-NLS-2$
            return rootNode1;
        } else if(sourceNode2 != null && sourceNode2.hasBooleanProperty(NodeConstants.Info.MAKE_DEP)) {
        	sourceNode2.recordDebugAnnotation("MAKE_DEP hint detected", null, "marking as dependent side of join", analysisRecord, null); //$NON-NLS-1$ //$NON-NLS-2$
            return rootNode2;
        } else if (sourceNode1.hasBooleanProperty(NodeConstants.Info.MAKE_IND) && sourceNode2 != null) {
        	sourceNode2.recordDebugAnnotation("MAKE_IND hint detected", null, "marking as dependent side of join", analysisRecord, null); //$NON-NLS-1$ //$NON-NLS-2$
        	return rootNode2;
        } else if (sourceNode2 != null && sourceNode2.hasBooleanProperty(NodeConstants.Info.MAKE_IND)) {
        	sourceNode1.recordDebugAnnotation("MAKE_IND hint detected", null, "marking as dependent side of join", analysisRecord, null); //$NON-NLS-1$ //$NON-NLS-2$
        	return rootNode1;
        }
        
        return null;
    }

    /**
     * Mark the specified access node to be made dependent
     * @param sourceNode Node to make dependent
     * @param dca 
     * @throws TeiidComponentException 
     * @throws QueryMetadataException 
     */
    boolean markDependent(PlanNode sourceNode, PlanNode joinNode, QueryMetadataInterface metadata, DependentCostAnalysis dca, Boolean bound) throws QueryMetadataException, TeiidComponentException {

        boolean isLeft = joinNode.getFirstChild() == sourceNode;
        
        // Get new access join node properties based on join criteria
        List independentExpressions = (List)(isLeft?joinNode.getProperty(NodeConstants.Info.RIGHT_EXPRESSIONS):joinNode.getProperty(NodeConstants.Info.LEFT_EXPRESSIONS)); 
        List dependentExpressions = (List)(isLeft?joinNode.getProperty(NodeConstants.Info.LEFT_EXPRESSIONS):joinNode.getProperty(NodeConstants.Info.RIGHT_EXPRESSIONS));
        
        if(independentExpressions == null || independentExpressions.isEmpty()) {
            return false;
        }

        String id = "$dsc/id" + ID.getAndIncrement(); //$NON-NLS-1$
        // Create DependentValueSource and set on the independent side as this will feed the values
        joinNode.setProperty(NodeConstants.Info.DEPENDENT_VALUE_SOURCE, id);
        
        PlanNode indNode = isLeft?joinNode.getLastChild():joinNode.getFirstChild();
        
        if (bound == null) {
        	List<PlanNode> sources = NodeEditor.findAllNodes(indNode, NodeConstants.Types.SOURCE);
        	for (PlanNode planNode : sources) {
				for (GroupSymbol gs : planNode.getGroups()) {
					if (gs.isTempTable() && metadata.getCardinality(gs.getMetadataID()) == QueryMetadataInterface.UNKNOWN_CARDINALITY) {
						bound = true;
						break;
					}
				}
			}
        	if (bound == null) {
        		bound = false;
        	}
        }

        PlanNode crit = getDependentCriteriaNode(id, independentExpressions, dependentExpressions, indNode, metadata, dca, bound);
        
        sourceNode.addAsParent(crit);
              
        if (isLeft) {
            JoinUtil.swapJoinChildren(joinNode);
        }
        return true;
    }

    /** 
     * @param independentExpressions
     * @param dependentExpressions
     * @return
     * @throws TeiidComponentException 
     * @throws QueryMetadataException 
     * @since 4.3
     */
    private PlanNode getDependentCriteriaNode(String id, List<Expression> independentExpressions,
                                           List<Expression> dependentExpressions, PlanNode indNode, QueryMetadataInterface metadata, DependentCostAnalysis dca, Boolean bound) throws QueryMetadataException, TeiidComponentException {
        
        Float cardinality = null;
        
        List<DependentSetCriteria.AttributeComparison> expressions = new ArrayList<DependentSetCriteria.AttributeComparison>(dependentExpressions.size());
        
        for (int i = 0; i < dependentExpressions.size(); i++) {
            Expression depExpr = dependentExpressions.get(i);
            Expression indExpr = independentExpressions.get(i);

            DependentSetCriteria.AttributeComparison comp = new DependentSetCriteria.AttributeComparison();
            if (dca != null && dca.expectedNdv[i] != null) {
            	if (dca.expectedNdv[i] > 4*dca.maxNdv[i]) {
            		continue; //not necessary to use
            	}
            	comp.ndv = dca.expectedNdv[i];
            	comp.maxNdv = dca.maxNdv[i];
            } else { 
                Collection<ElementSymbol> elems = ElementCollectorVisitor.getElements(indExpr, true);
                if (cardinality == null) {
                	cardinality = NewCalculateCostUtil.computeCostForTree(indNode, metadata);
                }
                comp.ndv = NewCalculateCostUtil.getNDVEstimate(indNode, metadata, cardinality, elems, true);
                if (bound) {
                	if (dca != null) {
                		comp.maxNdv = Math.max(comp.ndv * 4, dca.expectedCardinality * 2);
                	} else {
                		comp.maxNdv = Math.max(UNKNOWN_INDEPENDENT_CARDINALITY, comp.ndv * 4);
                	}
                }
            }
            comp.ind = indExpr;
            comp.dep = SymbolMap.getExpression(depExpr);
            expressions.add(comp);
        }

        return createDependentSetNode(id, expressions);

    }

	static PlanNode createDependentSetNode(String id, List<DependentSetCriteria.AttributeComparison> expressions) {
		DependentSetCriteria crit = createDependentSetCriteria(id, expressions);
		
        PlanNode selectNode = RelationalPlanner.createSelectNode(crit, false);
        
        selectNode.setProperty(NodeConstants.Info.IS_DEPENDENT_SET, Boolean.TRUE);
        return selectNode;
	}

	static DependentSetCriteria createDependentSetCriteria(String id, List<DependentSetCriteria.AttributeComparison> expressions) {
		if (expressions.isEmpty()) {
			return null;
		}
		
		Expression indEx = null;
        Expression depEx = null;
        float maxNdv = NewCalculateCostUtil.UNKNOWN_VALUE;
        float ndv = NewCalculateCostUtil.UNKNOWN_VALUE;
        if (expressions.size() == 1) {
        	AttributeComparison attributeComparison = expressions.get(0);
			indEx = attributeComparison.ind;
        	depEx = attributeComparison.dep;
        	maxNdv = attributeComparison.maxNdv;
        	ndv = attributeComparison.ndv;
        } else {
        	List<Expression> indExprs = new ArrayList<Expression>(expressions.size());
        	List<Expression> depExprs = new ArrayList<Expression>(expressions.size());
        	boolean unknown = false;
        	for (DependentSetCriteria.AttributeComparison comp : expressions) {
				indExprs.add(comp.ind);
				depExprs.add(comp.dep);
				if (comp.ndv == NewCalculateCostUtil.UNKNOWN_VALUE) {
					ndv = NewCalculateCostUtil.UNKNOWN_VALUE;
					maxNdv = NewCalculateCostUtil.UNKNOWN_VALUE;
					unknown = true;
				} else if (!unknown) {
					ndv = Math.max(ndv, comp.ndv);
		        	maxNdv = Math.max(maxNdv, comp.maxNdv);
				}
			}
        	//TODO: detect a base type
        	indEx = new Array(DefaultDataClasses.OBJECT, indExprs);
        	depEx = new Array(DefaultDataClasses.OBJECT, depExprs);
        }
        
        DependentSetCriteria crit = new DependentSetCriteria(depEx, id);
        crit.setValueExpression(indEx);
        crit.setAttributes(expressions);
        crit.setMaxNdv(maxNdv);
        crit.setNdv(ndv);
		return crit;
	}
    
    public String toString() {
        return "ChooseDependent"; //$NON-NLS-1$
    }
    
}
