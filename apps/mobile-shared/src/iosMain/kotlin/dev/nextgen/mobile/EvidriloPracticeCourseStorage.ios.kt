package dev.nextgen.mobile

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import dev.nextgen.mobile.storage.EncodedPracticeCourseStore
import dev.nextgen.mobile.storage.PracticeCourseStore
import platform.Foundation.NSUserDefaults

@Composable
internal actual fun rememberPracticeCourseStore(): PracticeCourseStore = remember {
    val defaults = NSUserDefaults.standardUserDefaults
    val key = "evidrilo.practice.course.v1"
    EncodedPracticeCourseStore(
        read = { defaults.stringForKey(key) },
        write = { value -> defaults.setObject(value, forKey = key); defaults.stringForKey(key) == value },
    )
}
