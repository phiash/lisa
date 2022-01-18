
package it.unive.lisa.imp.expressions;

import it.unive.lisa.analysis.AbstractState;
import it.unive.lisa.analysis.AnalysisState;
import it.unive.lisa.analysis.SemanticException;
import it.unive.lisa.analysis.heap.HeapDomain;
import it.unive.lisa.analysis.value.ValueDomain;
import it.unive.lisa.caches.Caches;
import it.unive.lisa.interprocedural.InterproceduralAnalysis;
import it.unive.lisa.program.SourceCodeLocation;
import it.unive.lisa.program.cfg.CFG;
import it.unive.lisa.program.cfg.statement.Expression;
import it.unive.lisa.symbolic.SymbolicExpression;
import it.unive.lisa.symbolic.value.BinaryExpression;
import it.unive.lisa.symbolic.value.operator.binary.BinaryOperator;
import it.unive.lisa.symbolic.value.operator.binary.NumericNonOverflowingAdd;
import it.unive.lisa.symbolic.value.operator.binary.StringConcat;
import it.unive.lisa.type.NumericType;
import it.unive.lisa.type.Type;
import it.unive.lisa.type.common.StringType;

/**
 * An expression modeling the plus operation ({@code +}) that, in some
 * languages, represents either the string concatenation or the numeric addition
 * depending on the types of its operands. If both operands' dynamic type
 * (according to {@link SymbolicExpression#getDynamicType()}) is a
 * {@link it.unive.lisa.type.StringType} (according to
 * {@link Type#isStringType()}), then this operation translates to a string
 * concatenation of its operands, and its type is {@link StringType}. Otherwise,
 * both operands' types must be instances of {@link NumericType}, and the type
 * of this expression (i.e., a numerical sum) is the common numerical type of
 * its operands, according to the type inference.
 * 
 * @author <a href="mailto:luca.negrini@unive.it">Luca Negrini</a>
 */
public class IMPAddOrConcat extends it.unive.lisa.program.cfg.statement.BinaryExpression {

	/**
	 * Builds the addition.
	 * 
	 * @param cfg        the {@link CFG} where this operation lies
	 * @param sourceFile the source file name where this operation is defined
	 * @param line       the line number where this operation is defined
	 * @param col        the column where this operation is defined
	 * @param left       the left-hand side of this operation
	 * @param right      the right-hand side of this operation
	 */
	public IMPAddOrConcat(CFG cfg, String sourceFile, int line, int col, Expression left, Expression right) {
		super(cfg, new SourceCodeLocation(sourceFile, line, col), "+", left, right);
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
		AnalysisState<A, H, V> result = state.bottom();
		BinaryOperator op;

		for (Type tleft : left.getTypes())
			for (Type tright : right.getTypes()) {
				if (tleft.isStringType())
					if (tright.isStringType() || tright.isUntyped())
						op = StringConcat.INSTANCE;
					else
						op = null;
				else if (tleft.isNumericType())
					if (tright.isNumericType() || tright.isUntyped())
						op = NumericNonOverflowingAdd.INSTANCE;
					else
						op = null;
				else if (tleft.isUntyped())
					if (tright.isStringType())
						op = StringConcat.INSTANCE;
					else if (tright.isNumericType() || tright.isUntyped())
						// arbitrary choice: if both are untyped, we consider it
						// as a numeric sum
						op = NumericNonOverflowingAdd.INSTANCE;
					else
						op = null;
				else
					op = null;

				if (op == null)
					continue;

				result = result.lub(state.smallStepSemantics(
						new BinaryExpression(
								op == StringConcat.INSTANCE
										? Caches.types().mkSingletonSet(StringType.INSTANCE)
										: getRuntimeTypes(),
								left,
								right,
								op,
								getLocation()),
						this));
			}

		return result;
	}
}