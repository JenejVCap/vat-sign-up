/*
 * Copyright 2018 HM Revenue & Customs
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
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

package uk.gov.hmrc.vatsignup.services

import javax.inject.{Inject, Singleton}

import cats.data._
import cats.implicits._
import uk.gov.hmrc.http.HeaderCarrier
import uk.gov.hmrc.vatsignup.connectors.{CustomerSignUpConnector, EmailVerificationConnector, RegistrationConnector, TaxEnrolmentsConnector}
import uk.gov.hmrc.vatsignup.httpparsers.{EmailNotVerified, EmailVerified, RegisterWithMultipleIdsSuccess, SuccessfulTaxEnrolment}
import uk.gov.hmrc.vatsignup.models.{CustomerSignUpResponseSuccess, SubscriptionRequest}
import uk.gov.hmrc.vatsignup.repositories.{EmailRequestRepository, SubscriptionRequestRepository}
import SignUpSubmissionService._
import play.api.mvc.Request
import uk.gov.hmrc.auth.core.Enrolments
import uk.gov.hmrc.vatsignup.config.Constants._
import uk.gov.hmrc.vatsignup.models.monitoring.RegisterWithMultipleIDsAuditing.RegisterWithMultipleIDsAuditModel
import uk.gov.hmrc.vatsignup.models.monitoring.SignUpAuditing.SignUpAuditModel
import uk.gov.hmrc.vatsignup.services.monitoring.AuditService
import uk.gov.hmrc.vatsignup.utils.EnrolmentUtils._

import scala.concurrent.{ExecutionContext, Future}

@Singleton
class SignUpSubmissionService @Inject()(subscriptionRequestRepository: SubscriptionRequestRepository,
                                        emailRequestRepository: EmailRequestRepository,
                                        emailVerificationConnector: EmailVerificationConnector,
                                        customerSignUpConnector: CustomerSignUpConnector,
                                        registrationConnector: RegistrationConnector,
                                        taxEnrolmentsConnector: TaxEnrolmentsConnector,
                                        auditService: AuditService
                                       )(implicit ec: ExecutionContext) {

  def submitSignUpRequest(vatNumber: String,
                          enrolments: Enrolments
                         )(implicit hc: HeaderCarrier,
                           request: Request[_]): Future[SignUpRequestSubmissionResponse] = {
    val optAgentReferenceNumber = enrolments.agentReferenceNumber
    val isDelegated = optAgentReferenceNumber.isDefined

    subscriptionRequestRepository.findById(vatNumber) flatMap {
      case Some(SubscriptionRequest(_, Some(companyNumber), None, Some(emailAddress), identityVerified)) if isDelegated || identityVerified =>
        val result = for {
          emailAddressVerified <- isEmailAddressVerified(emailAddress)
          safeId <- registerCompany(vatNumber, companyNumber, optAgentReferenceNumber)
          _ <- signUp(safeId, vatNumber, emailAddress, emailAddressVerified, optAgentReferenceNumber)
          _ <- registerEnrolment(vatNumber, safeId)
          _ <- deleteRecord(vatNumber)
          _ <- saveTemporarySubscriptionData(vatNumber, emailAddress, isDelegated)
        } yield SignUpRequestSubmitted

        result.value
      case Some(SubscriptionRequest(_, None, Some(nino), Some(emailAddress), identityVerified)) if isDelegated || identityVerified =>
        val result = for {
          emailAddressVerified <- isEmailAddressVerified(emailAddress)
          safeId <- registerIndividual(vatNumber, nino, optAgentReferenceNumber)
          _ <- signUp(safeId, vatNumber, emailAddress, emailAddressVerified, optAgentReferenceNumber)
          _ <- registerEnrolment(vatNumber, safeId)
          _ <- deleteRecord(vatNumber)
          _ <- saveTemporarySubscriptionData(vatNumber, emailAddress, isDelegated)
        } yield SignUpRequestSubmitted

        result.value
      case _ =>
        Future.successful(Left(InsufficientData))
    }
  }

  private def isEmailAddressVerified(emailAddress: String
                                    )(implicit hc: HeaderCarrier): EitherT[Future, SignUpRequestSubmissionFailure, Boolean] =
    EitherT(emailVerificationConnector.getEmailVerificationState(emailAddress)) bimap( {
      _ => EmailVerificationFailure
    }, {
      case EmailVerified => true
      case EmailNotVerified => false
    })

  private def registerCompany(vatNumber: String,
                              companyNumber: String,
                              agentReferenceNumber: Option[String]
                             )(implicit hc: HeaderCarrier, request: Request[_]): EitherT[Future, SignUpRequestSubmissionFailure, String] =
    EitherT(registrationConnector.registerCompany(vatNumber, companyNumber)) bimap( {
      _ => {
        auditService.audit(RegisterWithMultipleIDsAuditModel(vatNumber, Some(companyNumber), None, agentReferenceNumber, isSuccess = false))
        RegistrationFailure
      }
    }, {
      case RegisterWithMultipleIdsSuccess(safeId) => {
        auditService.audit(RegisterWithMultipleIDsAuditModel(vatNumber, Some(companyNumber), None, agentReferenceNumber, isSuccess = true))
        safeId
      }
    })

  private def registerIndividual(vatNumber: String,
                                 nino: String,
                                 agentReferenceNumber: Option[String]
                                )(implicit hc: HeaderCarrier, request: Request[_]): EitherT[Future, SignUpRequestSubmissionFailure, String] =
    EitherT(registrationConnector.registerIndividual(vatNumber, nino)) bimap( {
      _ => {
        auditService.audit(RegisterWithMultipleIDsAuditModel(vatNumber, None, Some(nino), agentReferenceNumber, isSuccess = false))
        RegistrationFailure
      }
    }, {
      case RegisterWithMultipleIdsSuccess(safeId) => {
        auditService.audit(RegisterWithMultipleIDsAuditModel(vatNumber, None, Some(nino), agentReferenceNumber, isSuccess = true))
        safeId
      }
    })

  private def signUp(safeId: String,
                     vatNumber: String,
                     emailAddress: String,
                     emailAddressVerified: Boolean,
                     agentReferenceNumber: Option[String]
                    )(implicit hc: HeaderCarrier, request: Request[_]): EitherT[Future, SignUpRequestSubmissionFailure, CustomerSignUpResponseSuccess.type] =
    if (agentReferenceNumber.isDefined || emailAddressVerified)
      EitherT(customerSignUpConnector.signUp(safeId, vatNumber, emailAddress, emailAddressVerified)) bimap( {
        _ => {
          auditService.audit(SignUpAuditModel(safeId, vatNumber, emailAddress, emailAddressVerified, agentReferenceNumber, false))
          SignUpFailure
        }
      }, {
        customerSignUpSuccess => {
          auditService.audit(SignUpAuditModel(safeId, vatNumber, emailAddress, emailAddressVerified, agentReferenceNumber, true))
          customerSignUpSuccess
        }
      })
    else EitherT.leftT(UnVerifiedPrincipalEmailFailure)


  private def registerEnrolment(vatNumber: String,
                                safeId: String
                               )(implicit hc: HeaderCarrier): EitherT[Future, SignUpRequestSubmissionFailure, SuccessfulTaxEnrolment.type] = {
    EitherT(taxEnrolmentsConnector.registerEnrolment(vatNumber, safeId)) leftMap {
      _ => EnrolmentFailure
    }
  }

  private def deleteRecord(vatNumber: String): EitherT[Future, SignUpRequestSubmissionFailure, SignUpRequestDeleted.type] = {
    val delete: Future[Either[SignUpRequestSubmissionFailure, SignUpRequestDeleted.type]] =
      subscriptionRequestRepository.deleteRecord(vatNumber).map(_ => Right(SignUpRequestDeleted))
    EitherT(delete) leftMap {
      _ => DeleteRecordFailure
    }
  }

  private def saveTemporarySubscriptionData(vatNumber: String, email: String, isDelegated: Boolean):
  EitherT[Future, SignUpRequestSubmissionFailure, StoreTemporarySubscriptionDataSuccess.type] = {
    val saveEmail: Future[Either[SignUpRequestSubmissionFailure, StoreTemporarySubscriptionDataSuccess.type]] =
      emailRequestRepository.upsertEmail(vatNumber, email, isDelegated).map(_ => Right(StoreTemporarySubscriptionDataSuccess))
    EitherT(saveEmail) leftMap {
      _ => DeleteRecordFailure
    }
  }
}

object SignUpSubmissionService {

  type SignUpRequestSubmissionResponse = Either[SignUpRequestSubmissionFailure, SignUpRequestSubmitted.type]

  case object SignUpRequestDeleted

  case object StoreTemporarySubscriptionDataSuccess

  case object SignUpRequestSubmitted

  sealed trait SignUpRequestSubmissionFailure

  case object InsufficientData extends SignUpRequestSubmissionFailure

  case object EmailVerificationFailure extends SignUpRequestSubmissionFailure

  case object UnVerifiedPrincipalEmailFailure extends SignUpRequestSubmissionFailure

  case object SignUpFailure extends SignUpRequestSubmissionFailure

  case object RegistrationFailure extends SignUpRequestSubmissionFailure

  case object EnrolmentFailure extends SignUpRequestSubmissionFailure

  case object DeleteRecordFailure extends SignUpRequestSubmissionFailure

}
