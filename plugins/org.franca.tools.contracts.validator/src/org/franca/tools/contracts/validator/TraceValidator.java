/*******************************************************************************
 * Copyright (c) 2013 itemis AG (http://www.itemis.de).
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *******************************************************************************/
package org.franca.tools.contracts.validator;

import java.util.Collection;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.WeakHashMap;

import org.franca.core.franca.FContract;
import org.franca.core.franca.FEventOnIf;
import org.franca.core.franca.FState;
import org.franca.core.franca.FTransition;

/**
 * This utility class contains methods to check whether a given trace matches a Franca {@link FContract}.
 * 
 * @author Tamas Szabo (itemis AG)
 *
 */
public class TraceValidator {

	/**
	 * Checks whether the given trace can be matched for the given Franca {@link FContract}.
	 * It is not necessary for the trace to start at the initial state of the {@link FContract}.
	 * It is equivalent to calling {@link #isValidTrace(FContract, List, null)}.
	 * 
	 * @param contract the contract that defines the state machine
	 * @param trace the saved trace as a list of events
	 * @return the validation result
	 */
	public static TraceValidationResult isValidTrace(FContract contract, List<FEventOnIf> trace) {
		return isValidTrace(contract, trace, null);
	}
	
	/**
	 * Checks whether the given trace can be matched for the given Franca {@link FContract} 
	 * where the initial set of transitions (for the first trace element) is also provided.
	 * It is not necessary for the trace to start at the initial state of the {@link FContract}.
	 * 
	 * @param contract the contract that defines the state machine 
	 * @param trace the saved trace as a list of events
	 * @param initialTransitions the set of initial transitions
	 * @return the validation result
	 */
	public static TraceValidationResult isValidTrace(FContract contract, List<FEventOnIf> trace, Set<FTransition> initialTransitions) {
		if (trace.size() == 0) {
			return TraceValidationResult.TRUE;
		}
		else {
			Map<FEventOnIf, Set<FTransition>> guessMap = constructGuessMap(contract);
			
			// one set inside the set corresponds to a given execution path (possible transition along one path)
			// it always holds that the starting state of the transitions inside a set is the same
			Set<Set<FTransition>> traceGroups = new HashSet<Set<FTransition>>();
			
			// to store the trace groups temporarily for the next trace element 
			// the elements correspond to different elements of paths but on the same level 
			Set<Set<FTransition>> temporaryTraceGroups = new HashSet<Set<FTransition>>();
			
			if (trace.size() == 1) {
				if (initialTransitions == null) {
					return (guessMap.get(trace.get(0)).size() > 0) ? 
							TraceValidationResult.TRUE : TraceValidationResult.FALSE;					
				}
				else {
					return (intersection(guessMap.get(trace.get(0)), initialTransitions).size() > 0) ? 
							TraceValidationResult.TRUE : TraceValidationResult.FALSE;	
				}
			}
			
			traceGroups.add((initialTransitions == null) ? guessMap.get(trace.get(0)) : initialTransitions);

			for (int i = 1; i < trace.size(); i++) {
				Set<FTransition> guess = guessMap.get(trace.get(i));

				// check whether we can follow the execution on any path
				Iterator<Set<FTransition>> iterator = traceGroups.iterator();
				while (iterator.hasNext()) {
					for (FTransition t : iterator.next()) {
						Set<FTransition> isect = intersection(t.getTo().getTransitions(), guess);
						
						// only add the new element if it contains at least one element
						// empty set indicates that the path cannot be followed
						if (!isect.isEmpty()) {
							temporaryTraceGroups.add(isect);
						}
					}
					
					iterator.remove();
				}

				// No transition can be found that matches the actual trace
				// element, it is not possible to follow further the execution
				// steps
				if (temporaryTraceGroups.isEmpty()) {
					return new TraceValidationResult(false, guess);
				}
				// Swap between temporary and actual groups
				Set<Set<FTransition>> tmp = temporaryTraceGroups;
				temporaryTraceGroups = traceGroups;
				traceGroups = tmp;
			}

			// if we reached this statement then the trace is a valid one
			return TraceValidationResult.TRUE;
		}
	}
	
	private static Set<FTransition> intersection(Collection<FTransition> left, Set<FTransition> right) {
		Set<FTransition> result = new HashSet<FTransition>(left);
		result.retainAll(right);
		return result;
	}
	
	/**
	 * Returns a map where the keys will be the events and the values are those transitions 
	 * which can be triggered by the given event.
	 * 
	 * @param contract the interface definition 
	 * @return the a map projecting the events to the triggered transitions
	 */
	private static Map<FEventOnIf, Set<FTransition>> constructGuessMap(FContract contract) {
		Map<FEventOnIf, Set<FTransition>> guessMap = new WeakHashMap<FEventOnIf, Set<FTransition>>();
		
		for (FState state : contract.getStateGraph().getStates()) {
			for (FTransition transition : state.getTransitions()) {
				FEventOnIf key = transition.getTrigger().getEvent();
				//String key = getCallName(transition);
				if (guessMap.get(key) == null) {
					Set<FTransition> transitions = new HashSet<FTransition>();
					transitions.add(transition);
					guessMap.put(key, transitions);
				}
				else {
					guessMap.get(key).add(transition);
				}
			}
		}
		
		return guessMap;
	}
}