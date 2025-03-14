/*
 * Copyright (C) 2018 Alexander Nutz (nutz@informatik.uni-freiburg.de)
 * Copyright (C) 2014-2015 Daniel Dietsch (dietsch@informatik.uni-freiburg.de)
 * Copyright (C) 2014-2015 Matthias Heizmann (heizmann@informatik.uni-freiburg.de)
 * Copyright (C) 2015 University of Freiburg
 *
 * This file is part of the ULTIMATE Core.
 *
 * The ULTIMATE Core is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as published
 * by the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * The ULTIMATE Core is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with the ULTIMATE Core. If not, see <http://www.gnu.org/licenses/>.
 *
 * Additional permission under GNU GPL version 3 section 7:
 * If you modify the ULTIMATE Core, or any covered work, by linking
 * or combining it with Eclipse RCP (or a modified version of Eclipse RCP),
 * containing parts covered by the terms of the Eclipse Public License, the
 * licensors of the ULTIMATE Core grant you additional permission
 * to convey the resulting work.
 */
package de.uni_freiburg.informatik.ultimate.boogie;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Iterator;
import java.util.List;
import java.util.function.Function;
import java.util.stream.Collectors;

import de.uni_freiburg.informatik.ultimate.boogie.ast.ArrayAccessExpression;
import de.uni_freiburg.informatik.ultimate.boogie.ast.ArrayLHS;
import de.uni_freiburg.informatik.ultimate.boogie.ast.ArrayStoreExpression;
import de.uni_freiburg.informatik.ultimate.boogie.ast.BinaryExpression;
import de.uni_freiburg.informatik.ultimate.boogie.ast.BinaryExpression.Operator;
import de.uni_freiburg.informatik.ultimate.boogie.ast.BitVectorAccessExpression;
import de.uni_freiburg.informatik.ultimate.boogie.ast.BitvecLiteral;
import de.uni_freiburg.informatik.ultimate.boogie.ast.BooleanLiteral;
import de.uni_freiburg.informatik.ultimate.boogie.ast.Expression;
import de.uni_freiburg.informatik.ultimate.boogie.ast.FunctionApplication;
import de.uni_freiburg.informatik.ultimate.boogie.ast.IdentifierExpression;
import de.uni_freiburg.informatik.ultimate.boogie.ast.IfThenElseExpression;
import de.uni_freiburg.informatik.ultimate.boogie.ast.IntegerLiteral;
import de.uni_freiburg.informatik.ultimate.boogie.ast.LeftHandSide;
import de.uni_freiburg.informatik.ultimate.boogie.ast.RealLiteral;
import de.uni_freiburg.informatik.ultimate.boogie.ast.StringLiteral;
import de.uni_freiburg.informatik.ultimate.boogie.ast.StructAccessExpression;
import de.uni_freiburg.informatik.ultimate.boogie.ast.StructConstructor;
import de.uni_freiburg.informatik.ultimate.boogie.ast.StructLHS;
import de.uni_freiburg.informatik.ultimate.boogie.ast.UnaryExpression;
import de.uni_freiburg.informatik.ultimate.boogie.ast.VariableLHS;
import de.uni_freiburg.informatik.ultimate.boogie.ast.WildcardExpression;
import de.uni_freiburg.informatik.ultimate.boogie.type.BoogieArrayType;
import de.uni_freiburg.informatik.ultimate.boogie.type.BoogieType;
import de.uni_freiburg.informatik.ultimate.boogie.typechecker.TypeCheckHelper;
import de.uni_freiburg.informatik.ultimate.core.model.models.ILocation;
import de.uni_freiburg.informatik.ultimate.logic.Rational;
import de.uni_freiburg.informatik.ultimate.util.ArithmeticUtils;

/**
 * Construct Boogie Expressions (and LeftHandSides), use this instead of the constructors. Functionalities:
 * <ul>
 * <li>do minor simplifications (e.g. if all operands are literals)
 * <li>computes types for the resulting Boogie expressions, throws an exception if it cannot be typed. Note that this
 * means that all input Boogie expressions must have a type. (Which is the case if they have been constructed using this
 * factory.)
 * </ul>
 *
 * @author Daniel Dietsch
 * @author Matthias Heizmann
 * @author Alexander Nutz (nutz@informatik.uni-freiburg.de)
 *
 */
public class ExpressionFactory {

	/**
	 * Name for dummy expressions that represent a "void" result. Those identifier expressions may not be used anywhere
	 * and thus should get an error BoogieType. (note that this string has to fit the regex that is checked during
	 * creation of an IdentifierExpression...)
	 */
	public static final String DUMMY_VOID = "#dummy~void~value";

	public static Expression constructUnaryExpression(final ILocation loc, final UnaryExpression.Operator operator,
			final Expression expr) {
		final BoogieType resultType = TypeCheckHelper.typeCheckUnaryExpression(operator, (BoogieType) expr.getType(),
				new TypeErrorReporter(loc));

		if (isLiteral(expr)) {
			switch (operator) {
			case ARITHNEGATIVE:
				if (expr instanceof IntegerLiteral) {
					final BigInteger value = new BigInteger(((IntegerLiteral) expr).getValue());
					return createIntegerLiteral(loc, value.negate().toString());
				} else if (expr instanceof RealLiteral) {
					final BigDecimal value = new BigDecimal(((RealLiteral) expr).getValue());
					return createRealLiteral(loc, value.negate().toString());
				} else {
					throw new IllegalArgumentException("type error: unable to apply " + operator);
				}
			case LOGICNEG:
				if (expr instanceof BooleanLiteral) {
					final boolean value = ((BooleanLiteral) expr).getValue();
					return createBooleanLiteral(loc, !value);
				}
				throw new IllegalArgumentException("type error: unable to apply " + operator);
			case OLD:
				return expr;
			default:
				throw new AssertionError("unknown operator " + operator);
			}
		}
		return new UnaryExpression(loc, resultType, operator, expr);
	}

	public static Expression newBinaryExpression(final ILocation loc, final Operator op, final Expression left,
			final Expression right) {

		final boolean isLeftLiteral = isLiteral(left);
		final boolean isRightLiteral = isLiteral(right);

		if (!isLeftLiteral && !isRightLiteral) {
			return constructBinaryExpression(loc, op, left, right);
		}

		if (!isLeftLiteral && isRightLiteral && isCommutative(op)) {
			// keep the literal on the left if commutative
			return newBinaryExpression(loc, op, right, left);
		}

		if (isLeftLiteral) {
			if (isNeutralLeft(op, left)) {
				return right;
			}
			if (isAnnihilatingLeft(op, left)) {
				return left;
			}
		}

		if (isRightLiteral) {
			if (isNeutralRight(op, right)) {
				return left;
			}
			if (isAnnihilatingRight(op, right)) {
				return right;
			}
		}

		if (isLeftLiteral && isRightLiteral) {
			return computeBinaryExpression(loc, op, left, right);
		} else if (isLeftLiteral && isCommutative(op)) {
			if (right instanceof BinaryExpression) {
				// if possible, try to combine constants
				// if expression is of the form (op c1 (op c2 x)), make (op c3 x) with c3 == (op c1 c2)
				final BinaryExpression rightBinExp = ((BinaryExpression) right);
				if (rightBinExp.getOperator() == op && isLiteral(rightBinExp.getLeft())) {
					return newBinaryExpression(loc, op, computeBinaryExpression(loc, op, left, rightBinExp.getLeft()),
							rightBinExp.getRight());
				}
			}
		}
		return constructBinaryExpression(loc, op, left, right);
	}

	private static Expression computeBinaryExpression(final ILocation loc, final Operator op, final Expression left,
			final Expression right) {
		if (left instanceof BooleanLiteral) {
			return constructBinExprWithLiteralOpsBool(loc, op, (BooleanLiteral) left, (BooleanLiteral) right);
		} else if (left instanceof IntegerLiteral) {
			return constructBinExprWithLiteralOpsInteger(loc, op, (IntegerLiteral) left, (IntegerLiteral) right);
		} else if (left instanceof RealLiteral) {
			return constructBinExprWithLiteralOpsReal(loc, op, (RealLiteral) left, (RealLiteral) right);
		} else if (left instanceof BitvecLiteral) {
			return constructBinExprWithLiteralOpsBitvector(loc, op, (BitvecLiteral) left, (BitvecLiteral) right);
		} else {
			throw new UnsupportedOperationException("Unknown literal: " + left.getClass());
		}
	}

	private static BooleanLiteral constructBinExprWithLiteralOpsBool(final ILocation loc, final Operator operator,
			final BooleanLiteral leftLiteral, final BooleanLiteral rightLiteral) {
		final boolean leftValue = leftLiteral.getValue();
		final boolean rightValue = rightLiteral.getValue();
		final boolean result;
		switch (operator) {
		case ARITHDIV:
		case ARITHMINUS:
		case ARITHMOD:
		case ARITHMUL:
		case ARITHPLUS:
		case BITVECCONCAT:
		case COMPGEQ:
		case COMPGT:
		case COMPLEQ:
		case COMPLT:
		case COMPPO:
			throw new IllegalArgumentException("type error: unable to apply " + operator + " to bool");
		case COMPEQ:
			result = leftValue == rightValue;
			break;
		case COMPNEQ:
			result = leftValue != rightValue;
			break;
		case LOGICAND:
			result = leftValue && rightValue;
			break;
		case LOGICIFF:
			result = leftValue == rightValue;
			break;
		case LOGICIMPLIES:
			result = !leftValue || rightValue;
			break;
		case LOGICOR:
			result = leftValue || rightValue;
			break;
		default:
			throw new AssertionError("unknown operator " + operator);
		}
		return createBooleanLiteral(loc, result);
	}

	private static Expression constructBinExprWithLiteralOpsInteger(final ILocation loc, final Operator operator,
			final IntegerLiteral leftLiteral, final IntegerLiteral rightLiteral) {
		final BigInteger leftValue = new BigInteger(leftLiteral.getValue());
		final BigInteger rightValue = new BigInteger(rightLiteral.getValue());
		switch (operator) {
		case ARITHDIV: {
			final BigInteger result = ArithmeticUtils.euclideanDiv(leftValue, rightValue);
			return createIntegerLiteral(loc, result.toString());
		}
		case ARITHMINUS: {
			final BigInteger result = leftValue.subtract(rightValue);
			return createIntegerLiteral(loc, result.toString());
		}
		case ARITHMOD: {
			final BigInteger result = ArithmeticUtils.euclideanMod(leftValue, rightValue);
			return createIntegerLiteral(loc, result.toString());
		}
		case ARITHMUL: {
			final BigInteger result = leftValue.multiply(rightValue);
			return createIntegerLiteral(loc, result.toString());
		}
		case ARITHPLUS: {
			final BigInteger result = leftValue.add(rightValue);
			return createIntegerLiteral(loc, result.toString());
		}
		case COMPEQ: {
			final boolean result = leftValue.compareTo(rightValue) == 0;
			return createBooleanLiteral(loc, result);
		}
		case COMPGEQ: {
			final boolean result = leftValue.compareTo(rightValue) >= 0;
			return createBooleanLiteral(loc, result);
		}
		case COMPGT: {
			final boolean result = leftValue.compareTo(rightValue) > 0;
			return createBooleanLiteral(loc, result);
		}
		case COMPLEQ: {
			final boolean result = leftValue.compareTo(rightValue) <= 0;
			return createBooleanLiteral(loc, result);
		}
		case COMPLT: {
			final boolean result = leftValue.compareTo(rightValue) < 0;
			return createBooleanLiteral(loc, result);
		}
		case COMPNEQ: {
			final boolean result = leftValue.compareTo(rightValue) != 0;
			return createBooleanLiteral(loc, result);
		}
		case BITVECCONCAT:
		case COMPPO:
		case LOGICAND:
		case LOGICIFF:
		case LOGICIMPLIES:
		case LOGICOR:
			throw new IllegalArgumentException("type error: unable to apply " + operator + " to bool");
		default:
			throw new AssertionError("unknown operator " + operator);
		}
	}

	private static Expression constructBinExprWithLiteralOpsReal(final ILocation loc, final Operator operator,
			final RealLiteral leftLiteral, final RealLiteral rightLiteral) {

		final Rational leftValue = toRational(leftLiteral.getValue());
		final Rational rightValue = toRational(rightLiteral.getValue());
		switch (operator) {
		case ARITHDIV: {
			final Rational result = leftValue.div(rightValue);
			return new RealLiteral(loc, BoogieType.TYPE_REAL, result.toString());
		}
		case ARITHMINUS: {
			final Rational result = leftValue.sub(rightValue);
			return new RealLiteral(loc, BoogieType.TYPE_REAL, result.toString());
		}
		case ARITHMUL: {
			final Rational result = leftValue.mul(rightValue);
			return new RealLiteral(loc, BoogieType.TYPE_REAL, result.toString());
		}
		case ARITHPLUS: {
			final Rational result = leftValue.add(rightValue);
			return new RealLiteral(loc, BoogieType.TYPE_REAL, result.toString());
		}
		case COMPEQ: {
			final boolean result = leftValue.compareTo(rightValue) >= 0;
			return createBooleanLiteral(loc, result);
		}
		case COMPGEQ: {
			final boolean result = leftValue.compareTo(rightValue) >= 0;
			return createBooleanLiteral(loc, result);
		}
		case COMPGT: {
			final boolean result = leftValue.compareTo(rightValue) >= 0;
			return createBooleanLiteral(loc, result);
		}
		case COMPLEQ: {
			final boolean result = leftValue.compareTo(rightValue) >= 0;
			return createBooleanLiteral(loc, result);
		}
		case COMPLT: {
			final boolean result = leftValue.compareTo(rightValue) >= 0;
			return createBooleanLiteral(loc, result);
		}
		case COMPNEQ: {
			final boolean result = leftValue.compareTo(rightValue) >= 0;
			return createBooleanLiteral(loc, result);
		}
		case ARITHMOD:
		case BITVECCONCAT:
		case COMPPO:
		case LOGICAND:
		case LOGICIFF:
		case LOGICIMPLIES:
		case LOGICOR:
			throw new IllegalArgumentException("type error: unable to apply " + operator + " to bool");
		default:
			throw new AssertionError("unknown operator " + operator);
		}
	}

	private static Expression constructBinExprWithLiteralOpsBitvector(final ILocation loc, final Operator operator,
			final BitvecLiteral leftLiteral, final BitvecLiteral rightLiteral) {
		final BigInteger leftValue = new BigInteger(leftLiteral.getValue());
		final BigInteger rightValue = new BigInteger(rightLiteral.getValue());
		if (leftValue.compareTo(BigInteger.ZERO) < 0) {
			throw new IllegalArgumentException("assumed non-negative number here!");
		}
		if (rightValue.compareTo(BigInteger.ZERO) < 0) {
			throw new IllegalArgumentException("assumed non-negativenumber here!");
		}
		final int leftLength = leftLiteral.getLength();
		final int rightLength = rightLiteral.getLength();

		switch (operator) {
		case COMPEQ: {
			if (leftLength != rightLength) {
				throw new IllegalArgumentException("type error: cannot compare bitvectors of differnt lengths");
			}
			final boolean result = leftValue.equals(rightValue);
			return createBooleanLiteral(loc, result);
		}
		case COMPNEQ: {
			if (leftLength != rightLength) {
				throw new IllegalArgumentException("type error: cannot compare bitvectors of differnt lengths");
			}
			final boolean result = !leftValue.equals(rightValue);
			return createBooleanLiteral(loc, result);
		}
		case BITVECCONCAT: {
			throw new UnsupportedOperationException("not yet implemented " + operator);
		}
		case ARITHDIV:
		case ARITHMINUS:
		case ARITHMOD:
		case ARITHMUL:
		case ARITHPLUS:
		case COMPGEQ:
		case COMPGT:
		case COMPLEQ:
		case COMPLT:
		case COMPPO:
		case LOGICAND:
		case LOGICIFF:
		case LOGICIMPLIES:
		case LOGICOR:
			throw new IllegalArgumentException("type error: unable to apply " + operator + " to bool");
		default:
			throw new AssertionError("unknown operator " + operator);
		}
	}

	public static Expression constructIfThenElseExpression(final ILocation loc, final Expression condition,
			final Expression thenPart, final Expression elsePart) {
		if (condition instanceof BooleanLiteral) {
			final boolean value = ((BooleanLiteral) condition).getValue();
			if (value) {
				return thenPart;
			}
			return elsePart;
		}
		final BoogieType type = TypeCheckHelper.typeCheckIfThenElseExpression((BoogieType) condition.getType(),
				(BoogieType) thenPart.getType(), (BoogieType) elsePart.getType(), new TypeErrorReporter(loc));
		return new IfThenElseExpression(loc, type, condition, thenPart, elsePart);

	}

	private static boolean isLiteral(final Expression expr) {
		return expr instanceof IntegerLiteral || expr instanceof BooleanLiteral || expr instanceof BitvecLiteral
				|| expr instanceof RealLiteral;
	}

	public static boolean isTrueLiteral(final Expression expr) {
		if (expr instanceof BooleanLiteral) {
			final BooleanLiteral bl = (BooleanLiteral) expr;
			if (bl.getValue()) {
				return true;
			}
		}
		return false;
	}

	/**
	 *
	 * @param loc
	 * @param operand
	 * @param high
	 *            exclusive
	 * @param low
	 *            inclusive
	 * @return
	 */
	public static Expression constructBitvectorAccessExpression(final ILocation loc, final Expression operand,
			final int high, final int low) {

		final BoogieType type = TypeCheckHelper.typeCheckBitVectorAccessExpression(
				TypeCheckHelper.getBitVecLength((BoogieType) operand.getType()), high, low,
				(BoogieType) operand.getType(), new TypeErrorReporter(loc));

		if (operand instanceof BitvecLiteral) {
			return constructBitvectorAccessExpressionResult(loc, (BitvecLiteral) operand, high, low, type);
		}
		return new BitVectorAccessExpression(loc, type, operand, high, low);
	}

	/**
	 * Result of BitvectorAccessExpression for the special case that the operand is a literal.
	 *
	 * @param high
	 *            exclusive
	 * @param low
	 *            inclusive
	 */
	public static Expression constructBitvectorAccessExpressionResult(final ILocation loc, final BitvecLiteral operand,
			final int high, final int low, final BoogieType type) {
		final BigInteger biresult =
				constructBitvectorAccessExpressionResult(new BigInteger(operand.getValue()), high, low);
		return new BitvecLiteral(loc, type, biresult.toString(), high - low);
	}

	public static BigInteger constructBitvectorAccessExpressionResult(final BigInteger value, final int high,
			final int low) {
		final BigInteger two = BigInteger.valueOf(2);
		final BigInteger dividedByLow = value.divide(two.pow(low));
		final BigInteger biresult = dividedByLow.mod(two.pow(high));
		return biresult;
	}

	public static Expression and(final ILocation loc, final List<Expression> exprs) {
		return bin(loc, exprs, true, Operator.LOGICAND);
	}

	public static Expression or(final ILocation loc, final List<Expression> exprs) {
		return bin(loc, exprs, false, Operator.LOGICOR);
	}

	private static Expression bin(final ILocation loc, final List<Expression> exprs, final boolean neutralElement,
			final Operator op) {
		if (exprs == null || exprs.isEmpty()) {
			return createBooleanLiteral(loc, neutralElement);
		}
		if (exprs.size() == 1) {
			return exprs.get(0);
		}

		final Iterator<Expression> iter = exprs.iterator();
		Expression current = iter.next();
		while (iter.hasNext()) {
			current = newBinaryExpression(loc, op, current, iter.next());
		}
		return current;
	}

	public static StructConstructor constructStructConstructor(final ILocation loc, final String[] fieldIds,
			final Expression[] fieldValues) {
		assert fieldIds.length == fieldValues.length;
		final BoogieType[] fieldTypes = new BoogieType[fieldIds.length];
		boolean hasError = false;
		for (int i = 0; i < fieldIds.length; i++) {
			fieldTypes[i] = (BoogieType) fieldValues[i].getType();
			hasError |= fieldValues[i].getType().equals(BoogieType.TYPE_ERROR);
		}
		final BoogieType type = hasError ? BoogieType.TYPE_ERROR : BoogieType.createStructType(fieldIds, fieldTypes);
		return new StructConstructor(loc, type, fieldIds, fieldValues);
	}

	public static StructLHS constructStructAccessLhs(final ILocation loc, final LeftHandSide struct,
			final String fieldName) {
		final BoogieType lhsType = TypeCheckHelper.typeCheckStructAccessExpressionOrLhs((BoogieType) struct.getType(),
				fieldName, new TypeErrorReporter(loc));
		return new StructLHS(loc, lhsType, struct, fieldName);
	}

	public static ArrayAccessExpression constructNestedArrayAccessExpression(final ILocation loc,
			final Expression array, final Expression[] indices) {
		if (indices.length == 0) {
			throw new AssertionError("attempting to build array access without indices");
		}

		if (indices.length == 1) {
			final BoogieArrayType arrayType = (BoogieArrayType) array.getType();
			final List<BoogieType> indicesTypes =
					Arrays.stream(indices).map(exp -> (BoogieType) exp.getType()).collect(Collectors.toList());
			final BoogieType newType = TypeCheckHelper.typeCheckArrayAccessExpressionOrLhs(arrayType, indicesTypes,
					new TypeErrorReporter(loc));
			return new ArrayAccessExpression(loc, newType, array, indices);
		}
		final Expression[] innerIndices = Arrays.copyOfRange(indices, 0, indices.length - 1);
		final Expression innerArrayAccessExpression = constructNestedArrayAccessExpression(loc, array, innerIndices);

		final Expression outerMostIndexValue = indices[indices.length - 1];
		final Expression[] outerMostIndex = new Expression[] { outerMostIndexValue };

		final BoogieArrayType arrayType = (BoogieArrayType) innerArrayAccessExpression.getType();
		final BoogieType newType = TypeCheckHelper.typeCheckArrayAccessExpressionOrLhs(arrayType,
				Arrays.asList(new BoogieType[] { (BoogieType) outerMostIndexValue.getType() }),
				new TypeErrorReporter(loc));

		return new ArrayAccessExpression(loc, newType, innerArrayAccessExpression, outerMostIndex);
	}

	public static ArrayLHS constructNestedArrayLHS(final ILocation loc, final LeftHandSide array,
			final Expression[] indices) {
		if (indices.length == 0) {
			throw new AssertionError("attempting to build array access without indices");
		}
		assert indices[0].getType() != null;
		assert array.getType() != null;

		if (indices.length == 1) {
			final BoogieArrayType arrayType = (BoogieArrayType) array.getType();
			final List<BoogieType> indicesTypes =
					Arrays.stream(indices).map(exp -> (BoogieType) exp.getType()).collect(Collectors.toList());
			final BoogieType lhsType = TypeCheckHelper.typeCheckArrayAccessExpressionOrLhs(arrayType, indicesTypes,
					new TypeErrorReporter(loc));
			return new ArrayLHS(loc, lhsType, array, indices);
		}
		final Expression[] innerIndices = Arrays.copyOfRange(indices, 0, indices.length - 1);
		final LeftHandSide innerLhs = constructNestedArrayLHS(loc, array, innerIndices);

		final Expression outerMostIndexValue = indices[indices.length - 1];
		final Expression[] outerMostIndex = new Expression[] { outerMostIndexValue };

		final BoogieArrayType arrayType = (BoogieArrayType) innerLhs.getType();
		// final List<BoogieType> indicesTypes = Arrays.stream(innerIndices)
		// .map(exp -> (BoogieType) exp.getType()).collect(Collectors.toList());
		final BoogieType lhsType = TypeCheckHelper.typeCheckArrayAccessExpressionOrLhs(arrayType,
				Arrays.asList(new BoogieType[] { (BoogieType) outerMostIndexValue.getType() }),
				new TypeErrorReporter(loc));

		return new ArrayLHS(loc, lhsType, innerLhs, outerMostIndex);
	}

	public static IdentifierExpression constructIdentifierExpression(final ILocation loc, final BoogieType type,
			final String identifier, final DeclarationInformation declarationInformation) {
		assert loc != null && type != null && identifier != null && declarationInformation != null;
		return new IdentifierExpression(loc, type, identifier, declarationInformation);
	}

	public static VariableLHS constructVariableLHS(final ILocation loc, final BoogieType type, final String identifier,
			final DeclarationInformation declarationInformation) {
		assert loc != null && type != null && identifier != null && declarationInformation != null;
		return new VariableLHS(loc, type, identifier, declarationInformation);
	}

	public static ArrayStoreExpression constructArrayStoreExpression(final ILocation loc, final Expression array,
			final Expression[] indices, final Expression value) {

		final List<BoogieType> indicesTypes = new ArrayList<>();
		for (final Expression index : indices) {
			indicesTypes.add((BoogieType) index.getType());
		}

		final BoogieType type = TypeCheckHelper.typeCheckArrayStoreExpression((BoogieType) array.getType(),
				indicesTypes, (BoogieType) value.getType(), new TypeErrorReporter(loc));
		return new ArrayStoreExpression(loc, type, array, indices, value);
	}

	private static BinaryExpression constructBinaryExpression(final ILocation loc, final Operator operator,
			final Expression operand1, final Expression operand2) {
		final BoogieType type = TypeCheckHelper.typeCheckBinaryExpression(operator, (BoogieType) operand1.getType(),
				(BoogieType) operand2.getType(), new TypeErrorReporter(loc));
		return new BinaryExpression(loc, type, operator, operand1, operand2);
	}

	/**
	 * Construct a new {@link FunctionApplication} with the given C function identifier and the given Boogie arguments.
	 *
	 * @param loc
	 *            A location
	 * @param identifier
	 *            The C function identifier
	 * @param arguments
	 *            The Boogie arguments
	 * @param resultBoogieType
	 *            the BoogieType of the result of the function application. This type must be provided because we cannot
	 *            track all FunctionDeclarations for type checking, as they might not have been constructed yet.
	 * @return A Boogie function application.
	 */
	public static Expression constructFunctionApplication(final ILocation loc, final String identifier,
			final Expression[] arguments, final BoogieType resultBoogieType) {
		return new FunctionApplication(loc, resultBoogieType, identifier, arguments);
	}

	public static StructAccessExpression constructStructAccessExpression(final ILocation loc, final Expression struct,
			final String fieldName) {
		final BoogieType type = TypeCheckHelper.typeCheckStructAccessExpressionOrLhs((BoogieType) struct.getType(),
				fieldName, new TypeErrorReporter(loc));
		return new StructAccessExpression(loc, type, struct, fieldName);
	}

	public static BooleanLiteral createBooleanLiteral(final ILocation loc, final boolean value) {
		return new BooleanLiteral(loc, BoogieType.TYPE_BOOL, value);
	}

	public static IntegerLiteral createIntegerLiteral(final ILocation loc, final String value) {
		return new IntegerLiteral(loc, BoogieType.TYPE_INT, value);
	}

	public static BitvecLiteral createBitvecLiteral(final ILocation loc, final String value, final int length) {
		return new BitvecLiteral(loc, BoogieType.createBitvectorType(length), value, length);
	}

	public static Expression createBitvecLiteral(final ILocation loc, BigInteger value, final int bitlength) {
		final Expression resultLiteral;
		if (value.signum() == -1) {
			final BigInteger maxValue = BigInteger.valueOf(2).pow(bitlength);
			value = value.add(maxValue);
		}
		final BigInteger valueInRange = constructBitvectorInRange(value, bitlength);
		resultLiteral = ExpressionFactory.createBitvecLiteral(loc, valueInRange.toString(), bitlength);
		return resultLiteral;
	}

	/**
	 * @return the result of value % 2^bitlength
	 */
	private static BigInteger constructBitvectorInRange(final BigInteger value, final int bitlength) {
		return value.mod(BigInteger.TWO.pow(bitlength));
	}

	public static RealLiteral createRealLiteral(final ILocation loc, final String value) {
		return new RealLiteral(loc, BoogieType.TYPE_REAL, value);
	}

	public static StringLiteral createStringLiteral(final ILocation loc, final String value) {
		// TODO: what boogie type should we give a string literal??
		return new StringLiteral(loc, value);
	}

	/**
	 * Returns an Expression that is the same as the given expression, except that its BoogieType has been changed to
	 * the given BoogieType.
	 *
	 * Note that this circumvents our type checks, thus should not be used for the final translation, as the type errors
	 * would occur in the BoogiePreprocessor.. (but is currently use in pre run mode).
	 *
	 * @param oe
	 * @param newType
	 * @return
	 */
	public static Expression replaceBoogieType(final Expression oe, final BoogieType newType) {
		return modifyExpression(oe, x -> x, x -> newType);
	}

	public static Expression modifyExpression(final Expression oe, final Function<ILocation, ILocation> oldToNewLoc,
			final Function<BoogieType, BoogieType> oldToNewType) {

		final ILocation newLoc = oldToNewLoc.apply(oe.getLoc());
		final BoogieType newType = oldToNewType.apply((BoogieType) oe.getType());

		assert newType != null;
		assert newLoc != null;

		if (oe instanceof ArrayAccessExpression) {
			return new ArrayAccessExpression(newLoc, newType, ((ArrayAccessExpression) oe).getArray(),
					((ArrayAccessExpression) oe).getIndices());
		} else if (oe instanceof ArrayStoreExpression) {
			return new ArrayStoreExpression(newLoc, newType, ((ArrayStoreExpression) oe).getArray(),
					((ArrayStoreExpression) oe).getIndices(), ((ArrayStoreExpression) oe).getValue());
		} else if (oe instanceof BinaryExpression) {
			return new BinaryExpression(newLoc, newType, ((BinaryExpression) oe).getOperator(),
					((BinaryExpression) oe).getLeft(), ((BinaryExpression) oe).getRight());
		} else if (oe instanceof BitvecLiteral) {
			return new BitvecLiteral(newLoc, newType, ((BitvecLiteral) oe).getValue(),
					((BitvecLiteral) oe).getLength());
		} else if (oe instanceof BitVectorAccessExpression) {
			return new BitVectorAccessExpression(newLoc, newType, ((BitVectorAccessExpression) oe).getBitvec(),
					((BitVectorAccessExpression) oe).getStart(), ((BitVectorAccessExpression) oe).getEnd());
		} else if (oe instanceof FunctionApplication) {
			return new FunctionApplication(newLoc, newType, ((FunctionApplication) oe).getIdentifier(),
					((FunctionApplication) oe).getArguments());
		} else if (oe instanceof IdentifierExpression) {
			return new IdentifierExpression(newLoc, newType, ((IdentifierExpression) oe).getIdentifier(),
					((IdentifierExpression) oe).getDeclarationInformation());
		} else if (oe instanceof StructConstructor) {
			return new StructConstructor(newLoc, newType, ((StructConstructor) oe).getFieldIdentifiers(),
					((StructConstructor) oe).getFieldValues());
		} else if (oe instanceof StructAccessExpression) {
			return new StructAccessExpression(newLoc, newType, ((StructAccessExpression) oe).getStruct(),
					((StructAccessExpression) oe).getField());
			// TODO implement these if needed
			// } else if (oe instanceof IfThenElseExpression) {
			// } else if (oe instanceof IntegerLiteral) {
			// } else if (oe instanceof QuantifierExpression) {
			// } else if (oe instanceof RealLiteral) {
			// } else if (oe instanceof StringLiteral) {
			// } else if (oe instanceof BooleanLiteral) {
			// } else if (oe instanceof UnaryExpression) {
		} else {
			throw new AssertionError("unexpected expression type: " + oe.getClass());
		}
	}

	public static IdentifierExpression createVoidDummyExpression(final ILocation loc) {
		return constructIdentifierExpression(loc, BoogieType.TYPE_ERROR, DUMMY_VOID,
				DeclarationInformation.DECLARATIONINFO_GLOBAL);
	}

	public static Expression constructBooleanWildCardExpression(final ILocation loc) {
		return new WildcardExpression(loc, BoogieType.TYPE_BOOL);
	}

	/**
	 * true iff left is neutral if it is the left operand of binOp, false otherwise
	 *
	 * If true, then (binOp left x) == x for any x
	 */
	private static boolean isNeutralLeft(final Operator binOp, final Expression left) {
		switch (binOp) {
		case ARITHMUL:
			if (left instanceof IntegerLiteral) {
				return new BigInteger(((IntegerLiteral) left).getValue()).equals(BigInteger.ONE);
			} else if (left instanceof RealLiteral) {
				return toRational(((RealLiteral) left).getValue()).equals(Rational.ONE);
			}
			return false;
		case ARITHPLUS:
			if (left instanceof IntegerLiteral) {
				return new BigInteger(((IntegerLiteral) left).getValue()).signum() == 0;
			} else if (left instanceof RealLiteral) {
				return toRational(((RealLiteral) left).getValue()).signum() == 0;
			}
			return false;
		case LOGICAND:
		case LOGICIMPLIES:
			return left instanceof BooleanLiteral && ((BooleanLiteral) left).getValue();
		case LOGICOR:
			return left instanceof BooleanLiteral && !((BooleanLiteral) left).getValue();
		case COMPEQ:
		case COMPNEQ:
		case ARITHDIV:
		case ARITHMINUS:
		case ARITHMOD:
		case BITVECCONCAT:
		case COMPGEQ:
		case COMPGT:
		case COMPLEQ:
		case COMPLT:
		case COMPPO:
		case LOGICIFF:
			return false;
		default:
			throw new UnsupportedOperationException("Currently unsupported: " + binOp);
		}
	}

	/**
	 * true iff right is neutral if it is the right operand of binOp, false otherwise
	 *
	 * If true, then (binOp right x) == x for any x
	 */
	private static boolean isNeutralRight(final Operator binOp, final Expression right) {
		// TODO: Complete
		switch (binOp) {
		case ARITHMUL:
		case ARITHPLUS:
		case COMPEQ:
		case COMPNEQ:
		case LOGICAND:
		case LOGICIFF:
		case LOGICOR:
			return isNeutralLeft(binOp, right);
		case ARITHDIV:
			return isNeutralLeft(Operator.ARITHMUL, right);
		case ARITHMINUS:
			return isNeutralLeft(Operator.ARITHPLUS, right);
		case ARITHMOD:
		case BITVECCONCAT:
		case COMPGEQ:
		case COMPGT:
		case COMPLEQ:
		case COMPLT:
		case COMPPO:
		case LOGICIMPLIES:
			return false;
		default:
			throw new UnsupportedOperationException("Currently unsupported: " + binOp);
		}
	}

	/**
	 * true iff binOp is commutative, false otherwise
	 */
	private static boolean isCommutative(final Operator binOp) {
		// TODO: Complete
		switch (binOp) {
		case ARITHMUL:
		case ARITHPLUS:
		case COMPEQ:
		case COMPNEQ:
		case LOGICAND:
		case LOGICIFF:
		case LOGICOR:
			return true;
		case ARITHDIV:
		case ARITHMINUS:
		case ARITHMOD:
		case BITVECCONCAT:
		case COMPGEQ:
		case COMPGT:
		case COMPLEQ:
		case COMPLT:
		case COMPPO:
		case LOGICIMPLIES:
			return false;
		default:
			throw new UnsupportedOperationException("Currently unsupported: " + binOp);
		}
	}

	/**
	 * true iff left is annihilating (or absorbing) if it is the left operand of binOp, false otherwise
	 *
	 * If true, then (binOp left x) == left for any x
	 */
	private static boolean isAnnihilatingLeft(final Operator binOp, final Expression left) {
		// TODO: Complete
		switch (binOp) {
		case ARITHMUL:
			if (left instanceof IntegerLiteral) {
				return new BigInteger(((IntegerLiteral) left).getValue()).signum() == 0;
			} else if (left instanceof RealLiteral) {
				return toRational(((RealLiteral) left).getValue()).signum() == 0;
			}
			return false;
		case LOGICAND:
			if (left instanceof BooleanLiteral) {
				return !((BooleanLiteral) left).getValue();
			}
			return false;
		case LOGICOR:
			if (left instanceof BooleanLiteral) {
				return ((BooleanLiteral) left).getValue();
			}
			return false;
		case ARITHPLUS:
		case ARITHDIV:
		case ARITHMINUS:
		case ARITHMOD:
		case BITVECCONCAT:
		case COMPEQ:
		case COMPGEQ:
		case COMPGT:
		case COMPLEQ:
		case COMPLT:
		case COMPNEQ:
		case COMPPO:
		case LOGICIFF:
		case LOGICIMPLIES:
			return false;
		default:
			throw new UnsupportedOperationException("Currently unsupported: " + binOp);
		}
	}

	/**
	 * true iff right is annihilating (or absorbing) if it is the right operand of binOp, false otherwise
	 *
	 * If true, then (binOp right x) == right for any x
	 */
	private static boolean isAnnihilatingRight(final Operator binOp, final Expression right) {
		// TODO: Complete
		switch (binOp) {
		case ARITHMUL:
		case ARITHPLUS:
		case COMPEQ:
		case COMPNEQ:
		case LOGICAND:
		case LOGICIFF:
		case LOGICOR:
			return isAnnihilatingLeft(binOp, right);
		case ARITHDIV:
		case ARITHMINUS:
		case ARITHMOD:
		case BITVECCONCAT:
		case COMPGEQ:
		case COMPGT:
		case COMPLEQ:
		case COMPLT:
		case COMPPO:
		case LOGICIMPLIES:
			return false;
		default:
			throw new UnsupportedOperationException("Currently unsupported: " + binOp);
		}
	}

	private static Rational toRational(final String realLiteralValue) {
		final String[] twoParts = realLiteralValue.split("/");
		if (twoParts.length == 2) {
			return Rational.valueOf(new BigInteger(twoParts[0]), new BigInteger(twoParts[1]));
		}
		if (twoParts.length == 1) {
			return toRational(new BigDecimal(realLiteralValue));
		}
		throw new IllegalArgumentException("Not a valid real literal value: " + realLiteralValue);
	}

	private static Rational toRational(final BigDecimal bigDec) {
		Rational rat;
		if (bigDec.scale() <= 0) {
			final BigInteger num = bigDec.toBigInteger();
			rat = Rational.valueOf(num, BigInteger.ONE);
		} else {
			final BigInteger num = bigDec.unscaledValue();
			final BigInteger denom = BigInteger.TEN.pow(bigDec.scale());
			rat = Rational.valueOf(num, denom);
		}
		return rat;
	}

}
