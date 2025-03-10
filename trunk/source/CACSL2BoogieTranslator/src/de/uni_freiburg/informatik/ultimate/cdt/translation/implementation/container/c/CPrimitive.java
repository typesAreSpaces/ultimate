/*
 * Copyright (C) 2013-2015 Alexander Nutz (nutz@informatik.uni-freiburg.de)
 * Copyright (C) 2012-2015 Markus Lindenmann (lindenmm@informatik.uni-freiburg.de)
 * Copyright (C) 2015 University of Freiburg
 *
 * This file is part of the ULTIMATE CACSL2BoogieTranslator plug-in.
 *
 * The ULTIMATE CACSL2BoogieTranslator plug-in is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as published
 * by the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * The ULTIMATE CACSL2BoogieTranslator plug-in is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with the ULTIMATE CACSL2BoogieTranslator plug-in. If not, see <http://www.gnu.org/licenses/>.
 *
 * Additional permission under GNU GPL version 3 section 7:
 * If you modify the ULTIMATE CACSL2BoogieTranslator plug-in, or any covered work, by linking
 * or combining it with Eclipse RCP (or a modified version of Eclipse RCP),
 * containing parts covered by the terms of the Eclipse Public License, the
 * licensors of the ULTIMATE CACSL2BoogieTranslator plug-in grant you additional permission
 * to convey the resulting work.
 */
/**
 * Describes a primitive variable given in C.
 */
package de.uni_freiburg.informatik.ultimate.cdt.translation.implementation.container.c;

import org.eclipse.cdt.core.dom.ast.IASTDeclSpecifier;
import org.eclipse.cdt.core.dom.ast.IASTSimpleDeclSpecifier;

/**
 * @author Markus Lindenmann
 * @date 13.07.2012
 */
public class CPrimitive extends CType {
	/**
	 * @author Markus Lindenmann
	 * @date 18.09.2012 Describing primitive C types. (updated 10.12.2013 by nutz)
	 */
	public enum CPrimitives {

		/* Integer Types */
		/* char */
		CHAR(CPrimitiveCategory.INTTYPE),
		/* signed char */
		SCHAR(CPrimitiveCategory.INTTYPE),
		/* unsigned char */
		UCHAR(CPrimitiveCategory.INTTYPE),
		/* ?? */
		// WCHAR,
		/* ?? */
		// CHAR32,
		/* ?? */
		// CHAR16,
		/* short, short int, signed short, signed short int */
		SHORT(CPrimitiveCategory.INTTYPE),
		/* unsigned short, unsigned short int */
		USHORT(CPrimitiveCategory.INTTYPE),
		/* int, signed int */
		INT(CPrimitiveCategory.INTTYPE),
		/* unsigned, unsigned int */
		UINT(CPrimitiveCategory.INTTYPE),
		/* long, long int, signed long, signed long int */
		LONG(CPrimitiveCategory.INTTYPE),
		/* unsigned long, unsigned long int */
		ULONG(CPrimitiveCategory.INTTYPE),
		/* long long, long long int, signed long long, signed long long int */
		LONGLONG(CPrimitiveCategory.INTTYPE),
		/* unsigned long long, unsigned long long int */
		ULONGLONG(CPrimitiveCategory.INTTYPE),
		/**
		 * signed __int128 from Section 6.9 of GNU C see https://gcc.gnu.org/onlinedocs/gcc/_005f_005fint128.html
		 */
		INT128(CPrimitiveCategory.INTTYPE),
		/**
		 * unsigned __int128 from Section 6.9 of GNU C see https://gcc.gnu.org/onlinedocs/gcc/_005f_005fint128.html
		 */
		UINT128(CPrimitiveCategory.INTTYPE),
		/* _Bool */
		BOOL(CPrimitiveCategory.INTTYPE),
		/* Floating Types */
		/* float */
		FLOAT(CPrimitiveCategory.FLOATTYPE),
		/* Float 128 https://gcc.gnu.org/onlinedocs/gcc/Floating-Types.html */
		FLOAT128(CPrimitiveCategory.FLOATTYPE),
		/* _Complex float */
		COMPLEX_FLOAT(CPrimitiveCategory.FLOATTYPE),
		/* double */
		DOUBLE(CPrimitiveCategory.FLOATTYPE),
		/* _Complex double */
		COMPLEX_DOUBLE(CPrimitiveCategory.FLOATTYPE),
		/* long double */
		LONGDOUBLE(CPrimitiveCategory.FLOATTYPE),
		/* _Complex long double */
		COMPLEX_LONGDOUBLE(CPrimitiveCategory.FLOATTYPE),
		// TODO: something with "_imaginary"??
		/* other type(s) */
		/**
		 * C type : void.
		 */
		VOID(CPrimitiveCategory.VOID);

		CPrimitives(final CPrimitiveCategory generalprimitive) {
			mPrimitiveCategory = generalprimitive;
		}

		private final CPrimitiveCategory mPrimitiveCategory;

		public boolean isIntegertype() {
			return mPrimitiveCategory == CPrimitiveCategory.INTTYPE;
		}

		public boolean isFloatingtype() {
			return mPrimitiveCategory == CPrimitiveCategory.FLOATTYPE;
		}

		public CPrimitiveCategory getPrimitiveCategory() {
			return mPrimitiveCategory;
		}

	}

	public enum CPrimitiveCategory {
		INTTYPE, FLOATTYPE, VOID
	}

	/**
	 * The C type of the variable.
	 */
	private final CPrimitives mType;

	/**
	 * more general type, i.e. inttype, floattype, void -- is derived from type
	 */
	private final CPrimitiveCategory mGeneralType;

	public CPrimitive(final CPrimitives type) {
		// FIXME: integrate those flags -- you will also need to change the equals method if you do
		super(false, false, false, false, false);
		mType = type;
		mGeneralType = getGeneralType(type);
	}

	/**
	 * Constructor.
	 *
	 * @param cDeclSpec
	 *            the C declaration specifier.
	 */
	public CPrimitive(final IASTDeclSpecifier cDeclSpec) {
		// FIXME: integrate those flags -- you will also need to change the equals method if you do
		super(false, false, false, false, false);
		if (!(cDeclSpec instanceof IASTSimpleDeclSpecifier)) {
			throw new IllegalArgumentException("Unknown C Declaration!");
		}
		mType = getType(cDeclSpec);
		mGeneralType = getGeneralType(mType);
	}

	private static CPrimitives getType(final IASTDeclSpecifier cDeclSpec) {
		final IASTSimpleDeclSpecifier sds = (IASTSimpleDeclSpecifier) cDeclSpec;
		switch (sds.getType()) {
		case IASTSimpleDeclSpecifier.t_bool:
			return CPrimitives.BOOL;
		case IASTSimpleDeclSpecifier.t_char:
			if (sds.isSigned()) {
				return CPrimitives.SCHAR;
			} else if (sds.isUnsigned()) {
				return CPrimitives.UCHAR;
			} else {
				return CPrimitives.CHAR;
			}
		case IASTSimpleDeclSpecifier.t_double:
			if (sds.isComplex()) {
				return sds.isLong() ? CPrimitives.COMPLEX_LONGDOUBLE : CPrimitives.COMPLEX_DOUBLE;
			} else if (sds.isLong()) {
				return CPrimitives.LONGDOUBLE;
			} else {
				return CPrimitives.DOUBLE;
			}
		case IASTSimpleDeclSpecifier.t_float:
			return sds.isComplex() ? CPrimitives.COMPLEX_FLOAT : CPrimitives.FLOAT;
		case IASTSimpleDeclSpecifier.t_int:
			if (sds.isUnsigned()) {
				if (sds.isLong()) {
					return CPrimitives.ULONG;
				} else if (sds.isLongLong()) {
					return CPrimitives.ULONGLONG;
				} else if (sds.isShort()) {
					return CPrimitives.USHORT;
				} else {
					return CPrimitives.UINT;
				}
			} else if (sds.isLong()) {
				return CPrimitives.LONG;
			} else if (sds.isLongLong()) {
				return CPrimitives.LONGLONG;
			} else if (sds.isShort()) {
				return CPrimitives.SHORT;
			} else {
				return CPrimitives.INT;
			}
		case IASTSimpleDeclSpecifier.t_unspecified:
			if (sds.isUnsigned()) {
				if (sds.isLong()) {
					return CPrimitives.ULONG;
				} else if (sds.isLongLong()) {
					return CPrimitives.ULONGLONG;
				} else if (sds.isShort()) {
					return CPrimitives.USHORT;
				} else {
					return CPrimitives.UINT;
				}
			} else if (sds.isLong()) {
				return CPrimitives.LONG;
			} else if (sds.isLongLong()) {
				return CPrimitives.LONGLONG;
			} else if (sds.isShort()) {
				return CPrimitives.SHORT;
			} else {
				return CPrimitives.INT;
			}
		case IASTSimpleDeclSpecifier.t_int128:
			return sds.isUnsigned() ? CPrimitives.UINT128 : CPrimitives.INT128;
		case IASTSimpleDeclSpecifier.t_float128:
			if (sds.isComplex()) {
				throw new UnsupportedOperationException("Complex 128bit floats are not supported yet.");
			}
			return CPrimitives.FLOAT128;
		case IASTSimpleDeclSpecifier.t_void:
			return CPrimitives.VOID;
		default:
			throw new IllegalArgumentException("Unknown C Declaration!");
		}
	}

	private static CPrimitiveCategory getGeneralType(final CPrimitives type) throws AssertionError {
		final CPrimitiveCategory generalType;
		switch (type) {
		case COMPLEX_FLOAT:
		case COMPLEX_DOUBLE:
		case COMPLEX_LONGDOUBLE:
		case FLOAT:
		case DOUBLE:
		case LONGDOUBLE:
		case FLOAT128:
			generalType = CPrimitiveCategory.FLOATTYPE;
			// throw new UnsupportedSyntaxException(LocationFactory.createIgnoreCLocation(), "we do not support
			// floats");
			break;
		case BOOL:
		case UCHAR:
		case UINT:
		case ULONG:
		case ULONGLONG:
		case UINT128:
		case USHORT:
		case CHAR:
			// case CHAR16:
			// case CHAR32:
		case INT:
		case LONG:
		case LONGLONG:
		case INT128:
		case SCHAR:
		case SHORT:
			// case WCHAR:
			generalType = CPrimitiveCategory.INTTYPE;
			break;
		case VOID:
			generalType = CPrimitiveCategory.VOID;
			break;
		default:
			throw new AssertionError("case missing");
		}
		return generalType;
	}

	public CPrimitives getType() {
		return mType;
	}

	public CPrimitiveCategory getGeneralType() {
		return mGeneralType;
	}

	@Override
	public String toString() {
		return mType.toString();
	}

	@Override
	public boolean isCompatibleWith(final CType o) {
		final CType oType = o.getUnderlyingType();

		if (oType instanceof CEnum && mGeneralType == CPrimitiveCategory.INTTYPE) {
			return true;
		}

		if (oType instanceof CPrimitive) {
			return mType == ((CPrimitive) oType).mType;
		}
		return false;
	}

	@Override
	public boolean isIncomplete() {
		return mType == CPrimitives.VOID;
	}

	@Override
	public int hashCode() {
		final int prime = 31;
		int result = super.hashCode();
		result = prime * result + (mGeneralType == null ? 0 : mGeneralType.hashCode());
		result = prime * result + (mType == null ? 0 : mType.hashCode());
		return result;
	}

	@Override
	public boolean equals(final Object o) {
		if (!(o instanceof CType)) {
			return false;
		}
		final CType oType = ((CType) o).getUnderlyingType();
		if (oType instanceof CPrimitive) {
			return mType == ((CPrimitive) oType).mType;
		}
		return false;
	}
}
