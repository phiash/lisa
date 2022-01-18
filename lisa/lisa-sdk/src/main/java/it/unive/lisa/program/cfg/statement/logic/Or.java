package it.unive.lisa.program.cfg.statement.logic;

import it.unive.lisa.analysis.AbstractState;
import it.unive.lisa.analysis.AnalysisState;
import it.unive.lisa.analysis.SemanticException;
import it.unive.lisa.analysis.heap.HeapDomain;
import it.unive.lisa.analysis.value.ValueDomain;
import it.unive.lisa.caches.Caches;
import it.unive.lisa.interprocedural.InterproceduralAnalysis;
import it.unive.lisa.program.cfg.CFG;
import it.unive.lisa.program.cfg.CodeLocation;
import it.unive.lisa.program.cfg.statement.Expression;
import it.unive.lisa.symbolic.SymbolicExpression;
import it.unive.lisa.symbolic.value.BinaryExpression;
import it.unive.lisa.symbolic.value.operator.binary.LogicalOr;
import it.unive.lisa.type.BooleanType;
import it.unive.lisa.type.common.BoolType;

/**
 * An expression modeling the logical disjunction ({@code ||} or {@code or}).
 * Both operands' types must be instances of {@link BooleanType}. The type of
 * this expression is the {@link BoolType}.
 * 
 * @author <a href="mailto:luca.negrini@unive.it">Luca Negrini</a>
 */
public class Or extends it.unive.lisa.program.cfg.statement.BinaryExpression {

	/**
	 * Builds the logical disjunction.
	 * 
	 * @param cfg      the {@link CFG} where this operation lies
	 * @param location the location where this literal is defined
	 * @param left     the left-hand side of this operation
	 * @param right    the right-hand side of this operation
	 */
	public Or(CFG cfg, CodeLocation location, Expression left, Expression right) {
		super(cfg, location, "||", BoolType.INSTANCE, left, right);
	}

	@Override
	protected <A extends AbstractState<A, H, V>,
			H extends HeapDomain<H>,
			V extends ValueDomain<V>> AnalysisState<A, H, V> binarySemantics(
					InterproceduralAnalysis<A, H, V> interprocedural,
					AnalysisState<A, H, V> state,
					SymbolicExpression left,
					SymbolicExpression right)
					throws SemanticException {
		// we allow untyped for the type inference phase
		if (!left.getDynamicType().isBooleanType() && !left.getDynamicType().isUntyped())
			return state.bottom();
		if (!right.getDynamicType().isBooleanType() && !right.getDynamicType().isUntyped())
			return state.bottom();

		return state.smallStepSemantics(
				new BinaryExpression(
						Caches.types().mkSingletonSet(BoolType.INSTANCE),
						left,
						right,
						LogicalOr.INSTANCE,
						getLocation()),
				this);
	}
}