/*
 * Copyright (c) 2020 Proton Technologies AG
 * 
 * This file is part of ProtonMail.
 * 
 * ProtonMail is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 * 
 * ProtonMail is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU General Public License for more details.
 * 
 * You should have received a copy of the GNU General Public License
 * along with ProtonMail. If not, see https://www.gnu.org/licenses/.
 */
package ch.protonmail.android.activities.navigation

import android.database.DataSetObserver
import android.widget.LinearLayout

/**
 * Created by Kamil Rajtar on 20.08.18.
 */
class LabelsViewController(private val labelsContainer:LinearLayout,
						   private val navigationLabelsAdapter:NavigationLabelsAdapter,
						   private val onLabelClick:Function1<String,Unit>):DataSetObserver() {

	override fun onChanged() {
		labelsContainer.removeAllViews()
		val count=navigationLabelsAdapter.count
		(0 until count).forEach {
			val labelItemView=navigationLabelsAdapter.getView(it,null,labelsContainer)
			val labelId=navigationLabelsAdapter.getItem(it)!!.label.id
			labelItemView.setOnClickListener {onLabelClick.invoke(labelId)}
			labelsContainer.addView(labelItemView)
		}
	}

	override fun onInvalidated() {
		labelsContainer.removeAllViews()
	}
}
