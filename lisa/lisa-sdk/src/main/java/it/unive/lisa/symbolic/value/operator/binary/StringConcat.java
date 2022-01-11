package it.unive.lisa.symbolic.value.operator.binary;

import it.unive.lisa.caches.Caches;
import it.unive.lisa.symbolic.value.BinaryExpression;
import it.unive.lisa.symbolic.value.operator.StringOperator;
import it.unive.lisa.type.Type;
import it.unive.lisa.type.common.StringType;
import it.unive.lisa.util.collections.externalSet.ExternalSet;

/**
 * Given two expressions that both evaluate to string values, a
 * {@link BinaryExpression} using this operator computes the concatenation of
 * the string from the first argument with the one of the second argument.<br>
 * <br>
 * First argument expression type: {@link StringType}<br>
 * Second argument expression type: {@link StringType}<br>
 * Computed expression type: {@link StringType}
 * 
 * @author <a href="mailto:luca.negrini@unive.it">Luca Negrini</a>
 */
public class StringConcat implements StringOperator, BinaryOperator {

	/**
	 * The singleton instance of this class.
	 */
	public static final StringConcat INSTANCE = new StringConcat();

	private StringConcat() {
	}

	@Override
	public String toString() {
		return "strcat";
	}

	@Override
	public ExternalSet<Type> typeInference(ExternalSet<Type> left, ExternalSet<Type> right) {
		if (left.noneMatch(Type::isStringType) || right.noneMatch(Type::isStringType))
			return Caches.types().mkEmptySet();
		return Caches.types().mkSingletonSet(StringType.INSTANCE);
	}
}