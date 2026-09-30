package dev.nextgen.mobile

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import dev.nextgen.mobile.storage.EncodedPracticeCourseStore
import dev.nextgen.mobile.storage.PracticeCourseStore
import dev.nextgen.mobile.storage.UnavailablePracticeCourseStore

@Composable
internal actual fun rememberPracticeCourseStore(): PracticeCourseStore {
    val context = LocalContext.current.applicationContext
    return remember(context) {
        runCatching<PracticeCourseStore> {
            val preferences = context.getSharedPreferences("evidrilo_practice_course_v1", Context.MODE_PRIVATE)
            EncodedPracticeCourseStore(
                read = { preferences.getString("course", null) },
                write = { value -> preferences.edit().putString("course", value).commit() },
            )
        }.getOrElse { UnavailablePracticeCourseStore() }
    }
}
