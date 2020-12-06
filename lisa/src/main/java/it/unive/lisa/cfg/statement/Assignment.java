package it.unive.lisa.cfg.statement;

import it.unive.lisa.analysis.AnalysisState;
import it.unive.lisa.analysis.ExpressionStore;
import it.unive.lisa.analysis.HeapDomain;
import it.unive.lisa.analysis.SemanticException;
import it.unive.lisa.analysis.ValueDomain;
import it.unive.lisa.analysis.impl.types.TypeEnvironment;
import it.unive.lisa.callgraph.CallGraph;
import it.unive.lisa.cfg.CFG;
import it.unive.lisa.symbolic.SymbolicExpression;
import it.unive.lisa.symbolic.value.Identifier;

/**
 * A statement assigning the result of an expression to an assignable
 * expression.
 * 
 * @author <a href="mailto:luca.negrini@unive.it">Luca Negrini</a>
 */
public class Assignment extends BinaryExpression {

	/**
	 * Builds the assignment, assigning {@code expression} to {@code target}.
	 * The location where this assignment happens is unknown (i.e. no source
	 * file/line/column is available).
	 * 
	 * @param cfg        the cfg that this statement belongs to
	 * @param target     the target of the assignment
	 * @param expression the expression to assign to {@code target}
	 */
	public Assignment(CFG cfg, Expression target, Expression expression) {
		this(cfg, null, -1, -1, target, expression);
	}

	/**
	 * Builds the assignment, assigning {@code expression} to {@code target},
	 * happening at the given location in the program.
	 * 
	 * @param cfg        the cfg that this statement belongs to
	 * @param sourceFile the source file where this statement happens. If
	 *                       unknown, use {@code null}
	 * @param line       the line number where this statement happens in the
	 *                       source file. If unknown, use {@code -1}
	 * @param col        the column where this statement happens in the source
	 *                       file. If unknown, use {@code -1}
	 * @param target     the target of the assignment
	 * @param expression the expression to assign to {@code target}
	 */
	public Assignment(CFG cfg, String sourceFile, int line, int col, Expression target, Expression expression) {
		super(cfg, sourceFile, line, col, target, expression);
	}

	@Override
	public final String toString() {
		return getLeft() + " = " + getRight();
	}

	@Override
	public <H extends HeapDomain<H>> AnalysisState<H, TypeEnvironment> typeInference(
			AnalysisState<H, TypeEnvironment> entryState, CallGraph callGraph,
			ExpressionStore<AnalysisState<H, TypeEnvironment>> expressions) throws SemanticException {
		AnalysisState<H, TypeEnvironment> right = getRight().typeInference(entryState, callGraph, expressions);
		AnalysisState<H, TypeEnvironment> left = getLeft().typeInference(right, callGraph, expressions);
		expressions.put(getRight(), right);
		expressions.put(getLeft(), left);

		AnalysisState<H, TypeEnvironment> result = null;
		for (SymbolicExpression expr1 : left.getComputedExpressions())
			for (SymbolicExpression expr2 : right.getComputedExpressions()) {
				AnalysisState<H, TypeEnvironment> tmp = left.assign((Identifier) expr1, expr2);
				if (result == null)
					result = tmp;
				else
					result = result.lub(tmp);
			}

		if (!getRight().getMetaVariables().isEmpty())
			result = result.forgetIdentifiers(getRight().getMetaVariables());
		if (!getLeft().getMetaVariables().isEmpty())
			result = result.forgetIdentifiers(getLeft().getMetaVariables());

		setRuntimeTypes(result.getState().getValueState().getLastComputedTypes().getRuntimeTypes());
		return result;
	}

	/**
	 * Semantics of an assignment ({@code left = right}) is evaluated as
	 * follows:
	 * <ol>
	 * <li>the semantic of the {@code right} is evaluated using the given
	 * {@code entryState}, returning a new analysis state
	 * {@code as_r = <state_r, expr_r>}</li>
	 * <li>the semantic of the {@code left} is evaluated using {@code as_r},
	 * returning a new analysis state {@code as_l = <state_l, expr_l>}</li>
	 * <li>the final post-state is evaluated through
	 * {@link AnalysisState#assign(Identifier, SymbolicExpression)}, using
	 * {@code expr_l} as {@code id} and {@code expr_r} as {@code value}</li>
	 * </ol>
	 * This means that all side effects from {@code right} are evaluated before
	 * the ones from {@code left}.<br>
	 * <br>
	 * {@inheritDoc}
	 */
	@Override
	public final <H extends HeapDomain<H>, V extends ValueDomain<V>> AnalysisState<H, V> semantics(
			AnalysisState<H, V> entryState, CallGraph callGraph, ExpressionStore<AnalysisState<H, V>> expressions)
			throws SemanticException {
		AnalysisState<H, V> right = getRight().semantics(entryState, callGraph, expressions);
		AnalysisState<H, V> left = getLeft().semantics(right, callGraph, expressions);
		expressions.put(getRight(), right);
		expressions.put(getLeft(), left);

		AnalysisState<H, V> result = null;
		for (SymbolicExpression expr1 : left.getComputedExpressions())
			for (SymbolicExpression expr2 : right.getComputedExpressions()) {
				AnalysisState<H, V> tmp = left.assign((Identifier) expr1, expr2);
				if (result == null)
					result = tmp;
				else
					result = result.lub(tmp);
			}

		if (!getRight().getMetaVariables().isEmpty())
			result = result.forgetIdentifiers(getRight().getMetaVariables());
		if (!getLeft().getMetaVariables().isEmpty())
			result = result.forgetIdentifiers(getLeft().getMetaVariables());
		return result;
	}
}
