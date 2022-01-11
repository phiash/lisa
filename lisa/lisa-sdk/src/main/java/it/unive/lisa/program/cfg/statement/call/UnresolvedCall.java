package it.unive.lisa.program.cfg.statement.call;

import it.unive.lisa.analysis.AbstractState;
import it.unive.lisa.analysis.AnalysisState;
import it.unive.lisa.analysis.SemanticException;
import it.unive.lisa.analysis.heap.HeapDomain;
import it.unive.lisa.analysis.lattices.ExpressionSet;
import it.unive.lisa.analysis.value.ValueDomain;
import it.unive.lisa.interprocedural.InterproceduralAnalysis;
import it.unive.lisa.interprocedural.callgraph.CallGraph;
import it.unive.lisa.interprocedural.callgraph.CallResolutionException;
import it.unive.lisa.program.cfg.CFG;
import it.unive.lisa.program.cfg.CodeLocation;
import it.unive.lisa.program.cfg.statement.Expression;
import it.unive.lisa.program.cfg.statement.call.resolution.ResolutionStrategy;
import it.unive.lisa.symbolic.SymbolicExpression;
import it.unive.lisa.type.Type;
import it.unive.lisa.type.Untyped;
import java.util.Objects;
import org.apache.commons.lang3.StringUtils;

/**
 * A call that happens inside the program to analyze. At this stage, the
 * target(s) of the call is (are) unknown. During the semantic computation, the
 * {@link CallGraph} used by the analysis will resolve this to a {@link CFGCall}
 * or to an {@link OpenCall}.
 * 
 * @author <a href="mailto:luca.negrini@unive.it">Luca Negrini</a>
 */
public class UnresolvedCall extends Call {

	/**
	 * The {@link ResolutionStrategy} of the parameters of this call
	 */
	private final ResolutionStrategy strategy;

	/**
	 * The name of the call target
	 */
	private final String targetName;

	/**
	 * Whether or not this is a call to an instance method of a unit (that can
	 * be overridden) or not.
	 */
	private final boolean instanceCall;

	/**
	 * Builds the CFG call, happening at the given location in the program. The
	 * static type of this CFGCall is the one return type of the descriptor of
	 * {@code target}.
	 * 
	 * @param cfg          the cfg that this expression belongs to
	 * @param location     the location where the expression is defined within
	 *                         the source file. If unknown, use {@code null}
	 * @param strategy     the {@link ResolutionStrategy} of the parameters of
	 *                         this call
	 * @param instanceCall whether or not this is a call to an instance method
	 *                         of a unit (that can be overridden) or not.
	 * @param targetName   the name of the target of this call
	 * @param parameters   the parameters of this call
	 */
	public UnresolvedCall(CFG cfg, CodeLocation location, ResolutionStrategy strategy,
			boolean instanceCall, String targetName, Expression... parameters) {
		this(cfg, location, strategy, instanceCall, targetName, Untyped.INSTANCE, parameters);
	}

	/**
	 * Builds the CFG call, happening at the given location in the program. The
	 * static type of this CFGCall is the one return type of the descriptor of
	 * {@code target}.
	 * 
	 * @param cfg          the cfg that this expression belongs to
	 * @param location     the location where the expression is defined within
	 *                         the source file. If unknown, use {@code null}
	 * @param strategy     the {@link ResolutionStrategy} of the parameters of
	 *                         this call
	 * @param instanceCall whether or not this is a call to an instance method
	 *                         of a unit (that can be overridden) or not.
	 * @param targetName   the name of the target of this call
	 * @param staticType   the static type of this call
	 * @param parameters   the parameters of this call
	 */
	public UnresolvedCall(CFG cfg, CodeLocation location, ResolutionStrategy strategy,
			boolean instanceCall, String targetName, Type staticType, Expression... parameters) {
		super(cfg, location, staticType, parameters);
		Objects.requireNonNull(targetName, "The target's name of an unresolved call cannot be null");
		this.strategy = strategy;
		this.targetName = targetName;
		this.instanceCall = instanceCall;
	}

	/**
	 * Yields the {@link ResolutionStrategy} of the parameters of this call.
	 * 
	 * @return the resolution strategy
	 */
	public ResolutionStrategy getStrategy() {
		return strategy;
	}

	/**
	 * Yields the name of the target of this call.
	 * 
	 * @return the name of the target
	 */
	public String getTargetName() {
		return targetName;
	}

	/**
	 * Yields whether or not this is a call to an instance method of a unit
	 * (that can be overridden) or not.
	 * 
	 * @return {@code true} if this call targets instance cfgs, {@code false}
	 *             otherwise
	 */
	public boolean isInstanceCall() {
		return instanceCall;
	}

	@Override
	public int hashCode() {
		final int prime = 31;
		int result = super.hashCode();
		result = prime * result + (instanceCall ? 1231 : 1237);
		result = prime * result + ((strategy == null) ? 0 : strategy.hashCode());
		result = prime * result + ((targetName == null) ? 0 : targetName.hashCode());
		return result;
	}

	@Override
	public boolean equals(Object obj) {
		if (this == obj)
			return true;
		if (!super.equals(obj))
			return false;
		if (getClass() != obj.getClass())
			return false;
		UnresolvedCall other = (UnresolvedCall) obj;
		if (instanceCall != other.instanceCall)
			return false;
		if (strategy != other.strategy)
			return false;
		if (targetName == null) {
			if (other.targetName != null)
				return false;
		} else if (!targetName.equals(other.targetName))
			return false;
		return true;
	}

	@Override
	public String toString() {
		return "[unresolved]" + targetName + "(" + StringUtils.join(getParameters(), ", ") + ")";
	}

	@Override
	public <A extends AbstractState<A, H, V>,
			H extends HeapDomain<H>,
			V extends ValueDomain<V>> AnalysisState<A, H, V> callSemantics(
					AnalysisState<A, H, V> entryState, InterproceduralAnalysis<A, H, V> interprocedural,
					AnalysisState<A, H, V>[] computedStates,
					ExpressionSet<SymbolicExpression>[] params)
					throws SemanticException {
		Call resolved;
		try {
			resolved = interprocedural.resolve(this);
		} catch (CallResolutionException e) {
			throw new SemanticException("Unable to resolve call " + this, e);
		}
		resolved.setRuntimeTypes(getRuntimeTypes());
		AnalysisState<A, H, V> result = resolved.callSemantics(entryState, interprocedural, computedStates, params);
		getMetaVariables().addAll(resolved.getMetaVariables());
		return result;
	}

	/**
	 * Updates this call's runtime types to match the ones of the given
	 * expression.
	 * 
	 * @param other the expression to inherit from
	 */
	public void inheritRuntimeTypesFrom(Expression other) {
		setRuntimeTypes(other.getRuntimeTypes());
	}
}
