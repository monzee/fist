package ph.codeia.fist.ktdemo

import android.os.Bundle
import android.support.v4.app.Fragment
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import ph.codeia.fist.*
import ph.codeia.fist.lifecycle.bind


/*
 * This file is a part of the fist project.
 */

class KtCounter : Fragment() {
    private lateinit var ui: Fst.Binding<Int, *>
    private lateinit var count: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        retainInstance = true
        ui = bind<Int>(BlockingFst(0)) {
            count.text = it.toString()
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater?,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ) = inflater?.inflate(R.layout.fragment_counter, container, false)?.also {
        count = it.findViewById(R.id.counter)
        it.findViewById<View>(R.id.plus).setOnClickListener { ui += succ }
        it.findViewById<View>(R.id.minus).setOnClickListener { ui += pred }
    }
}

private val succ = action<Int> { enter(it + 1) }

private val pred = action<Int> { enter(it - 1) }
