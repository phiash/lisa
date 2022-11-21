package it.unive.lisa.analysis.string.fsa.regex;

/**
 * A regular expression representing a loop, repeated an arbitrary number of
 * times, over an inner regular expression.
 * 
 * @author <a href="mailto:vincenzo.arceri@unive.it">Vincenzo Arceri</a>
 * @author <a href="mailto:luca.negrini@unive.it">Luca Negrini</a>
 */
public class Star extends RegularExpression {

	private RegularExpression op;

	/**
	 * Builds the star.
	 * 
	 * @param op the inner regular expression
	 */
	public Star(RegularExpression op) {
		this.op = op;
	}

	@Override
	public int hashCode() {
		return op.hashCode();
	}

	@Override
	public boolean equals(Object other) {
		return other instanceof Star && ((Star) other).getOperand().equals(op);
	}

	@Override
	public String toString() {
		// or already adds parentheses
		return op instanceof Or
				|| (op instanceof Atom && op.asAtom().toString().length() == 1) ? op.toString() + "*"
						: "(" + op.toString() + ")*";
	}

	/**
	 * Yields the inner regular expression.
	 * 
	 * @return the inner regular expression
	 */
	public RegularExpression getOperand() {
		return op;
	}

	@Override
	public RegularExpression simplify() {

		RegularExpression op = this.op.simplify();
		RegularExpression result;

		if (op instanceof Atom && op.asAtom().isEmpty())
			result = Atom.EPSILON;
		else if (op instanceof EmptySet)
			result = Atom.EPSILON;
		else if (op instanceof Star)
			result = op;
		else
			result = new Star(op);

		return result;
	}
}
