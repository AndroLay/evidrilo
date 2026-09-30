package dev.nextgen.mobile

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import dev.nextgen.mobile.storage.PracticeCourseStore
import dev.nextgen.mobile.storage.UnavailablePracticeCourseStore

@Composable
internal actual fun rememberPracticeCourseStore(): PracticeCourseStore = remember { UnavailablePracticeCourseStore() }
