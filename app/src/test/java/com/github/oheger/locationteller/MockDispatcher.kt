/*
 * Copyright 2019-2020 The Developers.
 *
 * Licensed under the Apache License, Version 2.0 (the "License")
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.github.oheger.locationteller

import io.kotest.core.listeners.TestListener
import io.kotest.core.test.TestCase
import io.kotest.core.test.TestResult
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import kotlin.coroutines.CoroutineContext


/**
 * A mock dispatcher implementation. This is used to check whether some
 * actions are correctly executed on the main thread. The dispatcher can be
 * configured to either execute scheduled blocks directly or to just record
 * them and execute them on an explicit request.
 * @param directExecution flag whether blocks are to be executed directly
 */
class MockDispatcher(private val directExecution: Boolean = true) : CoroutineDispatcher() {
    /** Stores the tasks that have been dispatched.*/
    val tasks = mutableListOf<Runnable>()

    /**
     * This implementation records the task to be executed and executes it
     * directly.
     */
    override fun dispatch(context: CoroutineContext, block: Runnable) {
        tasks += block
        if (directExecution) {
            block.run()
        }
    }

    /**
     * Executes all the tasks that have been recorded. Afterwards, the list of
     * tasks is reset. This function can be used if direct execution is
     * disabled to run the tasks on demand.
     */
    fun executeBlocks() {
        tasks.forEach { it.run() }
        tasks.clear()
    }

    companion object {
        /**
         * Creates a _MockDispatcher_ object and sets it for the main
         * dispatcher.
         * @return the mock dispatcher
         */
        @ExperimentalCoroutinesApi
        fun installAsMain(directExecution: Boolean = true): MockDispatcher {
            val mockDispatcher = MockDispatcher(directExecution)
            Dispatchers.setMain(mockDispatcher)
            return mockDispatcher
        }
    }
}

/**
 * A test listener that resets the main dispatcher after a test execution.
 * This is useful if the dispatcher has been modified to test the execution
 * of co-routines on the main thread.
 */
object ResetDispatcherListener : TestListener {
    @ExperimentalCoroutinesApi
    override suspend fun afterTest(testCase: TestCase, result: TestResult) {
        Dispatchers.resetMain()
    }
}
