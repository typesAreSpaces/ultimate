/*
 * Copyright (C) 2017 Matthias Heizmann (heizmann@informatik.uni-freiburg.de)
 * Copyright (C) 2017 University of Freiburg
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
package de.uni_freiburg.informatik.ultimate.core.lib.results;

import de.uni_freiburg.informatik.ultimate.core.model.models.IElement;
import de.uni_freiburg.informatik.ultimate.core.model.services.IBacktranslationService;
import de.uni_freiburg.informatik.ultimate.core.model.translation.IProgramExecution;
import de.uni_freiburg.informatik.ultimate.util.CoreUtil;

/**
 * Abstract superclass for Nontermination Arguments where the trace has the
 * form of a lasso.
 *
 * @author Matthias Heizmann (heizmann@informatik.uni-freiburg.de)
 * @param <P>
 *            program position class
 */
public class LassoShapedNonTerminationArgument<P extends IElement, E> extends NonTerminationArgumentResult<P, E> {

	private final IProgramExecution<P, E> mStemExecution;
	private final IProgramExecution<P, E> mLoopExecution;

	public LassoShapedNonTerminationArgument(final P element, final String plugin, final IBacktranslationService translatorSequence,
			final Class<E> exprClass, final IProgramExecution<P, E> stem, final IProgramExecution<P, E> loop) {
		super(element, plugin, translatorSequence, exprClass);
		mStemExecution = stem;
		mLoopExecution = loop;
	}

	public IProgramExecution<P, E> getStemExecution() {
		return mStemExecution;
	}

	public IProgramExecution<P, E> getLoopExecution() {
		return mLoopExecution;
	}


	@Override
	public String getShortDescription() {
		return "Nontermination argument in form of an infinite program execution.";
	}

	@Override
	public String getLongDescription() {
		final StringBuilder sb = new StringBuilder();
		sb.append("Nontermination argument in form of an infinite program execution.");
		sb.append(CoreUtil.getPlatformLineSeparator());
		sb.append("Stem:");
		sb.append(CoreUtil.getPlatformLineSeparator());
		sb.append(mTranslatorSequence.translateProgramExecution(mStemExecution));
		sb.append("Loop:");
		sb.append(CoreUtil.getPlatformLineSeparator());
		sb.append(mTranslatorSequence.translateProgramExecution(mLoopExecution));
		sb.append("End of lasso representation.");
		return sb.toString();
	}



}
