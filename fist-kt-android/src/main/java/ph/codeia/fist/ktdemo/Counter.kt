package ph.codeia.fist.ktdemo

import android.os.Bundle
import android.support.v4.app.Fragment
import android.view.LayoutInflater
import android.view.ViewGroup


/*
 * This file is a part of the fist project.
 */

class Counter : Fragment(), Renderer<Int> {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        retainInstance = true
    }

    override fun onCreateView(
            inflater: LayoutInflater?,
            container: ViewGroup?,
            savedInstanceState: Bundle?
    ) = inflater?.inflate(android.R.layout.activity_list_item, container, false).also {
    }

    override fun render(state: Int) {
    }
}

typealias Action<T> = Renderer<T>.() -> Unit

fun <T> render(f: () -> T): Action<T> = { render(f()) }

interface Renderer<in T> {
    fun render(state: T)
}

fun inc(n: Int) = render { n + 1 }

fun dec(n: Int) = render { n - 1 }