/*
 * Copyright 2019-2022 The Developers.
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
package com.github.oheger.locationteller.ui

import android.Manifest
import android.app.AlertDialog
import android.content.Context
import android.content.DialogInterface
import android.content.pm.PackageManager
import androidx.activity.result.ActivityResultCallback
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import com.github.oheger.locationteller.R
import io.kotest.core.spec.style.WordSpec
import io.kotest.matchers.shouldBe
import io.mockk.CapturingSlot
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.runs
import io.mockk.slot
import io.mockk.spyk
import io.mockk.verify

class LocationPermActionSpec : WordSpec({
    beforeAny {
        mockkStatic(ContextCompat::class)
    }

    "execute()" should {
        "call the action if the permission is available" {
            val helper = ActionTestHelper()

            helper.preparePermissionCheck(PackageManager.PERMISSION_GRANTED)
                .execute()
                .verifyActions(expActionCount = 1)
                .verifyNoRationaleDisplayed()
                .verifyRequestNotLaunched()
        }

        "launch the permission dialog without a rationale" {
            val helper = ActionTestHelper()

            helper.preparePermissionCheck(PackageManager.PERMISSION_DENIED)
                .preparePermissionRationale(show = false)
                .execute()
                .verifyActions(expActionCount = 0)
                .verifyNoRationaleDisplayed()
                .verifyRequestLaunched()
        }

        "display a dialog with a rationale" {
            val helper = ActionTestHelper()

            helper.preparePermissionCheck(PackageManager.PERMISSION_DENIED)
                .preparePermissionRationale(show = true)
                .execute()
                .verifyActions(expActionCount = 0)
                .verifyRequestNotLaunched()
                .verifyRationaleDisplayed()
        }
    }

    "the rationale dialog" should {
        "handle the cancel button" {
            val helper = ActionTestHelper()

            helper.preparePermissionCheck(PackageManager.PERMISSION_DENIED)
                .preparePermissionRationale(show = true)
                .execute()
                .rejectRationale()
                .verifyActions(expActionCount = 0, expRejectCount = 1)
                .verifyRequestNotLaunched()
        }

        "handle the continue button" {
            val helper = ActionTestHelper()

            helper.preparePermissionCheck(PackageManager.PERMISSION_DENIED)
                .preparePermissionRationale(show = true)
                .execute()
                .confirmRationale()
                .verifyActions(expActionCount = 0)
                .verifyRequestLaunched()
        }
    }

    "the activity result callback" should {
        "call the action if the permission is granted" {
            val helper = ActionTestHelper()

            helper.preparePermissionCheck(PackageManager.PERMISSION_DENIED)
                .preparePermissionRationale(show = false)
                .invokeResultCallback(granted = true)
                .verifyActions(expActionCount = 1)
        }

        "call the reject action if the permission is not granted" {
            val helper = ActionTestHelper()

            helper.preparePermissionCheck(PackageManager.PERMISSION_DENIED)
                .preparePermissionRationale(show = false)
                .invokeResultCallback(granted = false)
                .verifyActions(expActionCount = 0, expRejectCount = 1)
        }
    }
})

/**
 * A test helper class managing a test action and its dependencies.
 */
private class ActionTestHelper {
    /** Mock for the activity result launcher. */
    private val resultLauncher = createResultLauncher()

    /** A slot to capture the callback passed to the fragment. */
    private val slotCallback = slot<ActivityResultCallback<Boolean>>()

    /** A slot to capture the listener for the dialog OK button. */
    private val slotOkClickListener = slot<DialogInterface.OnClickListener>()

    /** A slot to capture the listener for the dialog Cancel button. */
    private val slotCancelClickListener = slot<DialogInterface.OnClickListener>()

    /** Mock for the fragment's context. */
    private val context = mockk<Context>()

    /** Mock for the associated fragment. */
    private val fragment = createFragmentMock()

    /** Counter for the invocations of the action. */
    private var actionCalls = 0

    /** Counter for the invocations of the reject action. */
    private var rejectCalls = 0

    /** The action to be tested. */
    private val testAction = LocationPermAction.create(fragment, this::action, this::rejectAction)

    /** A spy allowing access to some internals of the test action. */
    private val testActionSpy = createTestActionSpy(testAction)

    /**
     * Prepare the [ContextCompat] for a check of the location permission.
     * Return [result] as outcome of the check.
     */
    fun preparePermissionCheck(result: Int): ActionTestHelper {
        every { ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) } returns result
        return this
    }

    /**
     * Prepare the mock fragment for an invocation of the function that
     * determines whether to display a rationale for the permission request.
     * Return [show] from this function.
     */
    fun preparePermissionRationale(show: Boolean): ActionTestHelper {
        every { fragment.shouldShowRequestPermissionRationale(Manifest.permission.ACCESS_FINE_LOCATION) } returns show
        return this
    }

    /**
     * Execute the test action.
     */
    fun execute(): ActionTestHelper {
        testActionSpy.execute()
        return this
    }

    /**
     * Verify whether the wrapped action has been called [expActionCount]
     * times, and the reject action [expRejectCount] times.
     */
    fun verifyActions(expActionCount: Int, expRejectCount: Int = 0): ActionTestHelper {
        actionCalls shouldBe expActionCount
        rejectCalls shouldBe expRejectCount
        return this
    }

    /**
     * Verify that a request for the location permission has been launched.
     */
    fun verifyRequestLaunched(): ActionTestHelper {
        verify {
            resultLauncher.launch(Manifest.permission.ACCESS_FINE_LOCATION)
        }
        return this
    }

    /**
     * Verify that no request for the location permission has been launched.
     */
    fun verifyRequestNotLaunched(): ActionTestHelper {
        verify(exactly = 0) {
            resultLauncher.launch(any())
        }
        return this
    }

    /**
     * Verify that no dialog with a rationale was displayed.
     */
    fun verifyNoRationaleDisplayed(): ActionTestHelper {
        verify(exactly = 0) {
            testActionSpy.createDialogBuilder(any())
        }
        return this
    }

    /**
     * Verify that a correctly configured dialog with a rationale is displayed.
     */
    fun verifyRationaleDisplayed(): ActionTestHelper {
        val spy = testActionSpy.createDialogBuilder(context)

        verify {
            spy.setTitle(R.string.perm_location_title)
            spy.setMessage(R.string.perm_location_rationale)
            spy.show()
        }
        return this
    }

    /**
     * Simulate a click on the OK button of the rationale dialog.
     */
    fun confirmRationale(): ActionTestHelper = clickRationaleButton(slotOkClickListener)

    /**
     * Simulate a click on the Cancel button of the rationale dialog.
     */
    fun rejectRationale(): ActionTestHelper = clickRationaleButton(slotCancelClickListener)

    /**
     * Invoke the callback for the activity result with the given [granted]
     * flag.
     */
    fun invokeResultCallback(granted: Boolean): ActionTestHelper {
        slotCallback.captured.onActivityResult(granted)
        return this
    }

    /**
     * Simulate the action. Just increment a counter to record the invocation.
     */
    private fun action() {
        actionCalls += 1
    }

    /**
     * Simulate the reject action. Also increment a counter.
     */
    private fun rejectAction() {
        rejectCalls += 1
    }

    /**
     * Simulate a click on one of the buttons of the rationale dialog defined
     * by [slot].
     */
    private fun clickRationaleButton(slot: CapturingSlot<DialogInterface.OnClickListener>): ActionTestHelper {
        slot.captured.onClick(mockk(), 0)
        return this
    }

    /**
     * Create a mock for the [ActivityResultLauncher].
     */
    private fun createResultLauncher(): ActivityResultLauncher<String> {
        val mock = mockk<ActivityResultLauncher<String>>()

        every { mock.launch(Manifest.permission.ACCESS_FINE_LOCATION) } just runs

        return mock
    }

    /**
     * Create a mock fragment. Prepare the mock for the expected interactions.
     */
    private fun createFragmentMock(): Fragment {
        val mock = mockk<Fragment>()

        every {
            mock.registerForActivityResult(any<ActivityResultContracts.RequestPermission>(), capture(slotCallback))
        } returns resultLauncher
        every { mock.requireContext() } returns context

        return mock
    }

    /**
     * Create a spy that allows monitoring the interactions with the alert
     * dialog builder. The original builder is obtained from [action].
     */
    private fun createDialogBuilderSpy(action: LocationPermAction): AlertDialog.Builder {
        val spy = spyk(action.createDialogBuilder(context))

        every { spy.setPositiveButton(R.string.perm_button_continue, capture(slotOkClickListener)) } returns spy
        every { spy.setNegativeButton(R.string.perm_button_cancel, capture(slotCancelClickListener)) } returns spy

        return spy
    }

    /**
     * Create a spy for the test [action] that intercepts the creation of an
     * alert dialog builder.
     */
    private fun createTestActionSpy(action: LocationPermAction): LocationPermAction {
        val spy = spyk(action)
        val spyBuilder = createDialogBuilderSpy(action)

        every { spy.createDialogBuilder(context) } returns spyBuilder

        return spy
    }
}