/*
 * Copyright (C) 2019 Matthias Heizmann (heizmann@informatik.uni-freiburg.de)
 * Copyright (C) 2019 University of Freiburg
 *
 * This file is part of the ULTIMATE ModelCheckerUtils Library.
 *
 * The ULTIMATE ModelCheckerUtils Library is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as published
 * by the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * The ULTIMATE ModelCheckerUtils Library is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with the ULTIMATE ModelCheckerUtils Library. If not, see <http://www.gnu.org/licenses/>.
 *
 * Additional permission under GNU GPL version 3 section 7:
 * If you modify the ULTIMATE ModelCheckerUtils Library, or any covered work, by linking
 * or combining it with Eclipse RCP (or a modified version of Eclipse RCP),
 * containing parts covered by the terms of the Eclipse Public License, the
 * licensors of the ULTIMATE ModelCheckerUtils Library grant you additional permission
 * to convey the resulting work.
 */
package de.uni_freiburg.informatik.ultimate.lib.smtlibutils.arrays;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import java.util.stream.Collectors;

import de.uni_freiburg.informatik.ultimate.lib.smtlibutils.SmtSortUtils;
import de.uni_freiburg.informatik.ultimate.lib.smtlibutils.SmtUtils;
import de.uni_freiburg.informatik.ultimate.lib.smtlibutils.SubTermFinder;
import de.uni_freiburg.informatik.ultimate.lib.smtlibutils.binaryrelation.BinaryEqualityRelation;
import de.uni_freiburg.informatik.ultimate.lib.smtlibutils.quantifier.DualJunctionSaa;
import de.uni_freiburg.informatik.ultimate.logic.AnnotatedTerm;
import de.uni_freiburg.informatik.ultimate.logic.ApplicationTerm;
import de.uni_freiburg.informatik.ultimate.logic.ConstantTerm;
import de.uni_freiburg.informatik.ultimate.logic.LambdaTerm;
import de.uni_freiburg.informatik.ultimate.logic.LetTerm;
import de.uni_freiburg.informatik.ultimate.logic.MatchTerm;
import de.uni_freiburg.informatik.ultimate.logic.NonRecursive;
import de.uni_freiburg.informatik.ultimate.logic.QuantifiedFormula;
import de.uni_freiburg.informatik.ultimate.logic.Script;
import de.uni_freiburg.informatik.ultimate.logic.Term;
import de.uni_freiburg.informatik.ultimate.logic.TermVariable;

/**
 * Analyze for a given term and a given (wanted) array in which kinds of subterms the array occurs. Objects of this
 * class deliver the subterms for our array elimination {@link DualJunctionSaa}.
 *
 * @author Matthias Heizmann (heizmann@informatik.uni-freiburg.de)
 */
public class ArrayOccurrenceAnalysis {

	private static final boolean THROW_ERROR_BEFORE_DOWNGRADE = false;

	private final Term mAnalyzedTerm;
	private final Term mWantedArray;
	private final int mDimensionUpperLimit;

	private final List<MultiDimensionalSelectOverNestedStore> mArraySelectOverStores = new ArrayList<>();
	private final List<MultiDimensionalNestedStore> mNestedArrayStores = new ArrayList<>();
	private final List<MultiDimensionalSelect> mArraySelects = new ArrayList<>();
	private final List<BinaryEqualityRelation> mArrayEqualities = new ArrayList<>();
	private final List<BinaryEqualityRelation> mArrayDisequalities = new ArrayList<>();
	private final List<Term> mOtherFunctionApplications = new ArrayList<>();
	private final List<Term> mIsValueOfStore = new ArrayList<>();

	public ArrayOccurrenceAnalysis(final Script script, final Term analyzedTerm, final Term wantedArray) {
		mAnalyzedTerm = analyzedTerm;
		mWantedArray = wantedArray;
		mDimensionUpperLimit = Integer.MAX_VALUE;
		new ArrOccFinder(script, mAnalyzedTerm);
	}

	public ArrayOccurrenceAnalysis(final Script script, final Term analyzedTerm, final Term wantedArray,
			final int dimensionUpperLimit) {
		mAnalyzedTerm = analyzedTerm;
		mWantedArray = wantedArray;
		mDimensionUpperLimit = dimensionUpperLimit;
		new ArrOccFinder(script, mAnalyzedTerm);
	}

	/**
	 * @return from the analyzed term all select-over-store subterms whose array is the wantedArray
	 */
	public List<MultiDimensionalSelectOverNestedStore> getArraySelectOverStores() {
		return mArraySelectOverStores;
	}

	/**
	 * @return from the analyzed term all (possibly nested) store subterms whose array is the wantedArray such that the
	 *         store subterms are not part of a select-over-store subterm.
	 */
	public List<MultiDimensionalNestedStore> getNestedArrayStores() {
		return mNestedArrayStores;
	}

	/**
	 * @return from the analyzed term all select subterms whose array is the wantedArray
	 */
	public List<MultiDimensionalSelect> getArraySelects() {
		return mArraySelects;
	}

	/**
	 * @return from the analyzed term all binary equality subterms such that the wanted array is a subterm of one side
	 *         of the equality.
	 */
	public List<BinaryEqualityRelation> getArrayEqualities() {
		return mArrayEqualities;
	}

	/**
	 * @return from the analyzed term all binary disequality subterms such that the wanted array is a subterm of one
	 *         side of the disequality.
	 */
	public List<BinaryEqualityRelation> getArrayDisequalities() {
		return mArrayDisequalities;
	}

	/**
	 * @return from the analyzed term all function application subterms such that the wanted array is an argument.
	 *         However, as an exception all cases that are already handled by this class are omitted (i.e., if the
	 *         wanted array is first argument of store/select or occurs on one side of an equality/disequality)
	 */
	public List<Term> getOtherFunctionApplications() {
		return mOtherFunctionApplications;
	}

	public List<Term> getValueOfStore() {
		return mIsValueOfStore;
	}

	/**
	 * Returns subterms that our algorithm needs for elimination of anti-DER terms. These are the subterms where the
	 * wanted array occurs on one side (i.e., it is not sufficient that it is just a subterm on one side).
	 */
	public List<BinaryEqualityRelation> getDerRelations(final int quantifier) {
		if (quantifier == QuantifiedFormula.EXISTS) {
			return getArrayEqualities().stream().filter(x -> isRhsOrLhs(mWantedArray, x)).collect(Collectors.toList());
		}
		if (quantifier == QuantifiedFormula.FORALL) {
			return getArrayDisequalities().stream().filter(x -> isRhsOrLhs(mWantedArray, x))
					.collect(Collectors.toList());
		} else {
			throw new AssertionError("unknown quantifier");
		}
	}

	/**
	 * Returns subterms that our algorithm needs for elimination of anti-DER terms. These are the subterms where the
	 * wanted array is a subterm of one side (i.e., is not necessarily the element on one side).
	 */
	public List<BinaryEqualityRelation> getAntiDerRelations(final int quantifier) {
		if (quantifier == QuantifiedFormula.EXISTS) {
			return getArrayDisequalities();
		}
		if (quantifier == QuantifiedFormula.FORALL) {
			return getArrayEqualities();
		} else {
			throw new AssertionError("unknown quantifier");
		}
	}

	public TreeSet<Integer> computeSelectAndStoreDimensions() {
		final TreeSet<Integer> result = new TreeSet<>();
		for (final MultiDimensionalSelect mds : getArraySelects()) {
			result.add(mds.getDimension());
		}
		for (final MultiDimensionalNestedStore mds : getNestedArrayStores()) {
			result.add(mds.getDimension());
		}
		return result;
	}

	public ArrayOccurrenceAnalysis downgradeDimensionsIfNecessary(final Script script) {
		final TreeSet<Integer> dims = computeSelectAndStoreDimensions();
		if (dims.size() <= 1) {
			return this;
		}
		final int dimensionUpperLimit = dims.first();
		assert dimensionUpperLimit >= 1;
		return new ArrayOccurrenceAnalysis(script, mAnalyzedTerm, mWantedArray, dimensionUpperLimit);
	}

	/**
	 * @return true iff target is rhs or lhs of the {@link BinaryEqualityRelation}
	 */
	private static boolean isRhsOrLhs(final Term target, final BinaryEqualityRelation ber) {
		return ber.getLhs().equals(target) || ber.getRhs().equals(target);
	}

	private class ArrOccFinder extends NonRecursive {
		private final Script mScript;
		private final Set<Term> mTermsInWhichWeAlreadyDescended = new HashSet<>();

		public ArrOccFinder(final Script script, final Term term) {
			mScript = script;
			run(new MyWalker(term));
		}

		class MyWalker extends TermWalker {

			MyWalker(final Term term) {
				super(term);
			}

			@Override
			public void walk(final NonRecursive walker) {
				if (mTermsInWhichWeAlreadyDescended.contains(getTerm())) {
					// do nothing
				} else {
					super.walk(walker);
				}
			}

			@Override
			public void walk(final NonRecursive walker, final ConstantTerm term) {
				// cannot descend
			}

			@Override
			public void walk(final NonRecursive walker, final AnnotatedTerm term) {
				mTermsInWhichWeAlreadyDescended.add(term);
				walker.enqueueWalker(new MyWalker(term.getSubterm()));
			}

			@Override
			public void walk(final NonRecursive walker, final ApplicationTerm term) {
				mTermsInWhichWeAlreadyDescended.add(term);
				final String fun = term.getFunction().getName();
				if (fun.equals("=")) {
					if (term.getParameters().length != 2) {
						throw new UnsupportedOperationException("expecting equality with two parameters");
					}
					if (SmtSortUtils.isArraySort(term.getParameters()[0].getSort())
							&& isSubtermOfSome(mWantedArray, term.getParameters()[0], term.getParameters()[1])) {
						mArrayEqualities.add(constructBinaryEqualityRelation(term));
					}
					walker.enqueueWalker(new MyWalker(term.getParameters()[0]));
					walker.enqueueWalker(new MyWalker(term.getParameters()[1]));
				} else if (fun.equals("distinct")) {
					throw new UnsupportedOperationException("UNF requires negated equality");
				} else if (fun.equals("not")) {
					assert term.getParameters().length == 1;
					if (!SmtUtils.isAtomicFormula(term.getParameters()[0])) {
						throw new UnsupportedOperationException("expected NNF");
					}
					final Term negatedAtom = term.getParameters()[0];
					if (negatedAtom instanceof ApplicationTerm) {
						final ApplicationTerm negatedAppTerm = (ApplicationTerm) negatedAtom;
						if (negatedAppTerm.getFunction().getName().equals("=")) {
							if (negatedAppTerm.getParameters().length != 2) {
								throw new UnsupportedOperationException("expecting equality with two parameters");
							}
							if (SmtSortUtils.isArraySort(negatedAppTerm.getParameters()[0].getSort())
									&& isSubtermOfSome(mWantedArray, negatedAppTerm.getParameters()[0],
											negatedAppTerm.getParameters()[1])) {
								mArrayDisequalities.add(constructBinaryEqualityRelation(term));
							}
							walker.enqueueWalker(new MyWalker(negatedAppTerm.getParameters()[0]));
							walker.enqueueWalker(new MyWalker(negatedAppTerm.getParameters()[1]));
						} else {
							walker.enqueueWalker(new MyWalker(negatedAtom));
						}
					} else {
						walker.enqueueWalker(new MyWalker(negatedAtom));
					}
				} else if (fun.equals("store")) {
					MultiDimensionalNestedStore nas = MultiDimensionalNestedStore.convert(mScript, term);
					if (nas != null) {
						final Set<Term> swwaiv =
								SubTermFinder.find(term, x -> isStoreWhereWantedArrayIsValue(x, mWantedArray), false);
						mIsValueOfStore.addAll(swwaiv);
					}
					if (nas != null && nas.getArray().equals(mWantedArray)) {
						if (THROW_ERROR_BEFORE_DOWNGRADE && nas
								.getDimension() != new MultiDimensionalSort(mWantedArray.getSort()).getDimension()) {
							throw new AssertionError("downgrade");
						}
						// TODO 2019-11-24 Matthias: unify downgrade for
						// MultiDimensionalNestedStore and MultiDimensionalSelectOverNestedStore
						if (nas.getDimension() > mDimensionUpperLimit) {
							if (THROW_ERROR_BEFORE_DOWNGRADE) {
								throw new AssertionError("downgrade");
							}
							nas = new MultiDimensionalNestedStore(MultiDimensionalStore
									.convert(nas.getInnermost().getStoreTerm(), mDimensionUpperLimit));
							assert nas.getArray() == mWantedArray;
						}
						assert nas.getArray() == mWantedArray;
						mNestedArrayStores.add(nas);
						for (final ArrayIndex ai : nas.getIndices()) {
							for (final Term indexEntry : ai) {
								walker.enqueueWalker(new MyWalker(indexEntry));
							}
						}
						for (final Term value : nas.getValues()) {
							walker.enqueueWalker(new MyWalker(value));
						}
					} else {
						for (final Term t : term.getParameters()) {
							walker.enqueueWalker(new MyWalker(t));
						}
					}
				} else if (fun.equals("select")) {
					final MultiDimensionalSelectOverNestedStore asos =
							MultiDimensionalSelectOverNestedStore.convert(mScript, term);
					if (asos != null) {
						if (asos.getNestedStore().getArray().equals(mWantedArray)) {
							if (asos.getNestedStore().getDimension() > mDimensionUpperLimit) {
								// TODO 2019-11-24 Matthias: unify downgrade for
								// MultiDimensionalNestedStore and MultiDimensionalSelectOverNestedStore
								if (THROW_ERROR_BEFORE_DOWNGRADE) {
									throw new AssertionError("downgrade");
								}
								for (final Term t : term.getParameters()) {
									walker.enqueueWalker(new MyWalker(t));
								}
								return;
							}
							mArraySelectOverStores.add(asos);
							for (final Term indexEntry : asos.getSelect().getIndex()) {
								walker.enqueueWalker(new MyWalker(indexEntry));
							}
							for (final ArrayIndex ai : asos.getNestedStore().getIndices()) {
								for (final Term indexEntry : ai) {
									walker.enqueueWalker(new MyWalker(indexEntry));
								}
							}
							for (final Term value : asos.getNestedStore().getValues()) {
								walker.enqueueWalker(new MyWalker(value));
							}
						} else {
							for (final Term t : term.getParameters()) {
								walker.enqueueWalker(new MyWalker(t));
							}
						}
					} else {
						final MultiDimensionalSelect as = MultiDimensionalSelect.of(term);
						// If this select is above our dimension limit we ignore this select and descend
						// to all children
						if (as.getIndex().size() > 0 && as.getArray().equals(mWantedArray)
								&& as.getDimension() <= mDimensionUpperLimit) {
							mArraySelects.add(as);
							for (final Term indexEntry : as.getIndex()) {
								walker.enqueueWalker(new MyWalker(indexEntry));
							}
						} else {
							for (final Term t : term.getParameters()) {
								walker.enqueueWalker(new MyWalker(t));
							}
						}
					}
				} else {
					for (final Term t : term.getParameters()) {
						if (t.equals(mWantedArray)) {
							mOtherFunctionApplications.add(t);
						} else {
							walker.enqueueWalker(new MyWalker(t));
						}
					}
				}
			}

			private BinaryEqualityRelation constructBinaryEqualityRelation(final ApplicationTerm term) {
				final BinaryEqualityRelation ber = BinaryEqualityRelation.convert(term);
				if (ber == null) {
					throw new AssertionError("Cannot convert relation");
				}
				return ber;
			}

			@Override
			public void walk(final NonRecursive walker, final LetTerm term) {
				throw new UnsupportedOperationException();
			}

			@Override
			public void walk(final NonRecursive walker, final QuantifiedFormula term) {
				walker.enqueueWalker(new MyWalker(term.getSubformula()));
			}

			@Override
			public void walk(final NonRecursive walker, final TermVariable term) {
				// cannot descend
			}

			@Override
			public void walk(final NonRecursive walker, final MatchTerm term) {
				throw new UnsupportedOperationException("not yet implemented: MatchTerm");
			}

			@Override
			public void walk(final NonRecursive walker, final LambdaTerm term) {
				throw new UnsupportedOperationException();
			}

		}

	}

	/**
	 * @return true iff the candidate is a subterm of some of the terms.
	 */
	private static boolean isSubtermOfSome(final Term subtermCandidate, final Term... terms) {
		return Arrays.stream(terms).anyMatch(x -> SmtUtils.isSubterm(x, subtermCandidate));
	}

	public static Set<ArrayIndex> extractSelectIndices(final List<MultiDimensionalSelect> arraySelects) {
		return arraySelects.stream().map(MultiDimensionalSelect::getIndex).collect(Collectors.toSet());
	}

	public static Set<ArrayIndex> extractStoreIndices(final List<MultiDimensionalStore> mds) {
		return mds.stream().map(MultiDimensionalStore::getIndex).collect(Collectors.toSet());
	}

	public static Set<ArrayIndex> extractNestedStoreIndices(final List<MultiDimensionalNestedStore> arraySelects) {
		return arraySelects.stream().map(MultiDimensionalNestedStore::getIndices).flatMap(List::stream)
				.collect(Collectors.toSet());
	}

	public static boolean isStoreWhereWantedArrayIsValue(final Term term, final Term wantedArray) {
		final ArrayStore as = ArrayStore.convert(term);
		if (as == null) {
			return false;
		}
		return as.getValue().equals(wantedArray);
	}

}
