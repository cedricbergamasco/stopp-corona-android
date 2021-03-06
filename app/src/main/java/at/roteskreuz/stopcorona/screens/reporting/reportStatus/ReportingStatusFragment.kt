package at.roteskreuz.stopcorona.screens.reporting.reportStatus

import android.os.Bundle
import android.view.View
import androidx.appcompat.app.ActionBar
import androidx.appcompat.widget.Toolbar
import at.roteskreuz.stopcorona.R
import at.roteskreuz.stopcorona.model.api.SicknessCertificateUploadException
import at.roteskreuz.stopcorona.model.entities.infection.message.MessageType
import at.roteskreuz.stopcorona.model.exceptions.handleBaseCoronaErrors
import at.roteskreuz.stopcorona.model.repositories.ReportingRepository
import at.roteskreuz.stopcorona.screens.base.dialog.GeneralErrorDialog
import at.roteskreuz.stopcorona.screens.questionnaire.success.startQuestionnaireReportSuccessFragment
import at.roteskreuz.stopcorona.screens.reporting.personalData.ReportingPersonalDataFragment
import at.roteskreuz.stopcorona.screens.reporting.personalData.ReportingPersonalDataFragment.Companion.SCROLLED_DISTANCE_THRESHOLD
import at.roteskreuz.stopcorona.screens.reporting.reportStatus.success.startCertificateReportSuccessFragment
import at.roteskreuz.stopcorona.screens.reporting.reportStatus.success.startRevokeSuspicionSuccessFragment
import at.roteskreuz.stopcorona.skeleton.core.model.helpers.DataState
import at.roteskreuz.stopcorona.skeleton.core.model.helpers.State
import at.roteskreuz.stopcorona.skeleton.core.model.scope.connectToScope
import at.roteskreuz.stopcorona.skeleton.core.screens.base.fragment.BaseFragment
import at.roteskreuz.stopcorona.skeleton.core.utils.dip
import at.roteskreuz.stopcorona.skeleton.core.utils.dipif
import at.roteskreuz.stopcorona.skeleton.core.utils.observeOnMainThread
import at.roteskreuz.stopcorona.utils.view.AccurateScrollListener
import at.roteskreuz.stopcorona.utils.view.LinearLayoutManagerAccurateOffset
import io.reactivex.rxkotlin.plusAssign
import kotlinx.android.synthetic.main.fragment_reporting_status.*
import org.koin.androidx.viewmodel.ext.android.viewModel

/**
 * Screen presenting the status of the reporting process, part of the flow
 * for reporting a medical certificate or the result of a self-testing to the authorities.
 */
class ReportingStatusFragment : BaseFragment(R.layout.fragment_reporting_status) {

    companion object {
        const val CURRENT_SCREEN = 3
    }

    private val viewModel: ReportingStatusViewModel by viewModel()

    override val isToolbarVisible: Boolean = true

    override fun getTitle(): String? {
        return getString(R.string.certificate_report_status_title)
    }

    private val controller: ReportingStatusController by lazy {
        ReportingStatusController(
            context = requireContext(),
            onAgreementCheckboxChange = viewModel::setUserAgreement,
            onSendReportClick = viewModel::uploadInfectionInformation
        )
    }

    private val accurateScrollListener by lazy {
        AccurateScrollListener(
            onScroll = { scrolledDistance ->
                transparentAppBar.elevation = if (scrolledDistance > requireContext().dip(SCROLLED_DISTANCE_THRESHOLD)) {
                    requireContext().dipif(4)
                } else {
                    0f
                }
            }
        )
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        connectToScope(ReportingRepository.SCOPE_NAME)
        super.onCreate(savedInstanceState)
    }

    override fun onInitActionBar(actionBar: ActionBar?, toolbar: Toolbar?) {
        super.onInitActionBar(actionBar, toolbar)
        actionBar?.setDisplayHomeAsUpEnabled(true)
        toolbar?.setNavigationIcon(R.drawable.ic_back)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        txtProgress.text = getString(
            R.string.certificate_personal_progress_label,
            CURRENT_SCREEN,
            ReportingPersonalDataFragment.TOTAL_NUMBER_OF_SCREENS
        )

        with(contentRecyclerView) {
            setController(controller)
            layoutManager = LinearLayoutManagerAccurateOffset(requireContext(), accurateScrollListener)
            addOnScrollListener(accurateScrollListener)
        }

        disposables += viewModel.observeReportingStatusData()
            .observeOnMainThread()
            .subscribe { statusData ->
                controller.agreementData = statusData.agreementData
                controller.messageType = statusData.messageType
                controller.dateOfFirstSelfDiagnose = statusData.dateOfFirstSelfDiagnose
            }

        disposables += viewModel.observeUploadReportDataState()
            .observeOnMainThread()
            .subscribe { state ->
                hideProgressDialog()
                when (state) {
                    is State.Loading -> {
                        showProgressDialog(R.string.general_loading)
                    }
                    is DataState.Loaded -> {
                        when (state.data) {
                            MessageType.InfectionLevel.Red -> startCertificateReportSuccessFragment()
                            MessageType.InfectionLevel.Yellow -> startQuestionnaireReportSuccessFragment()
                            MessageType.Revoke -> startRevokeSuspicionSuccessFragment()
                        }

                        activity?.finish()
                    }
                    is State.Error -> {
                        when (state.error) {
                            is SicknessCertificateUploadException.TanInvalidException -> {
                                GeneralErrorDialog(R.string.certificate_report_status_invalid_tan_error,
                                    R.string.certificate_report_status_invalid_tan_error_description).show()
                            }
                            is SicknessCertificateUploadException.BirthdayInvalidException -> {
                                GeneralErrorDialog(R.string.certificate_report_status_invalid_birth_date_error,
                                    R.string.certificate_report_status_invalid_birth_date_error_description).show()
                            }
                            else -> handleBaseCoronaErrors(state.error)
                        }
                    }
                }
            }
    }

    override fun onDestroyView() {
        contentRecyclerView.removeOnScrollListener(accurateScrollListener)
        super.onDestroyView()
    }

    override fun overrideOnBackPressed(): Boolean {
        viewModel.goBack()
        return true // the changing of fragments is managing parent activity
    }
}
