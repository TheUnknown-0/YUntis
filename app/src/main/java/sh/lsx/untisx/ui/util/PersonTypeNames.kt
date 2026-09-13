package sh.lsx.untisx.ui.util

import sh.lsx.untisx.R

fun personTypeName(type: Int): UiText = when (type) {
    2 -> UiText.Res(R.string.person_teacher)
    5 -> UiText.Res(R.string.person_student)
    else -> UiText.Raw(type.toString())
}
