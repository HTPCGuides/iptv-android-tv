package com.iptvtv.ui

import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewmodel.CreationExtras
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import androidx.lifecycle.createSavedStateHandle
import com.iptvtv.AppContainer
import com.iptvtv.IptvApp

/** The app's dependency graph, from wherever a screen is composed. */
@Composable
fun appContainer(): AppContainer = (LocalContext.current.applicationContext as IptvApp).container

/**
 * A ViewModel built from the app container — the hand-wired stand-in for `hiltViewModel()`.
 * The [SavedStateHandle] carries the navigation route's arguments.
 */
@Composable
inline fun <reified VM : ViewModel> containerViewModel(
    crossinline create: CreationExtras.(AppContainer, SavedStateHandle) -> VM,
): VM {
    val container = appContainer()
    return viewModel(factory = viewModelFactory { initializer { create(container, createSavedStateHandle()) } })
}
