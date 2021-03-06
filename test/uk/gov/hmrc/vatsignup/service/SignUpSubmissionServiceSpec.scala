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

package uk.gov.hmrc.vatsignup.service

import org.scalatest.EitherValues
import play.api.http.Status._
import play.api.test.FakeRequest
import reactivemongo.api.commands.{UpdateWriteResult, WriteResult}
import uk.gov.hmrc.auth.core.Enrolments
import uk.gov.hmrc.http.HeaderCarrier
import uk.gov.hmrc.play.test.UnitSpec
import uk.gov.hmrc.vatsignup.connectors.mocks.{MockCustomerSignUpConnector, MockRegistrationConnector, MockTaxEnrolmentsConnector}
import uk.gov.hmrc.vatsignup.helpers.TestConstants
import uk.gov.hmrc.vatsignup.helpers.TestConstants._
import uk.gov.hmrc.vatsignup.httpparsers.RegisterWithMultipleIdentifiersHttpParser._
import uk.gov.hmrc.vatsignup.httpparsers.TaxEnrolmentsHttpParser._
import uk.gov.hmrc.vatsignup.models._
import uk.gov.hmrc.vatsignup.models.monitoring.RegisterWithMultipleIDsAuditing.RegisterWithMultipleIDsAuditModel
import uk.gov.hmrc.vatsignup.models.monitoring.SignUpAuditing.SignUpAuditModel
import uk.gov.hmrc.vatsignup.repositories.mocks.{MockEmailRequestRepository, MockSubscriptionRequestRepository}
import uk.gov.hmrc.vatsignup.service.mocks.MockEmailRequirementService
import uk.gov.hmrc.vatsignup.service.mocks.monitoring.MockAuditService
import uk.gov.hmrc.vatsignup.services.EmailRequirementService.{Email, GetEmailVerificationFailure, UnVerifiedEmail}
import uk.gov.hmrc.vatsignup.services.SignUpSubmissionService._
import uk.gov.hmrc.vatsignup.services._

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

class SignUpSubmissionServiceSpec extends UnitSpec with EitherValues
  with MockSubscriptionRequestRepository with MockEmailRequirementService
  with MockCustomerSignUpConnector with MockRegistrationConnector
  with MockTaxEnrolmentsConnector with MockAuditService with MockEmailRequestRepository {

  object TestSignUpSubmissionService extends SignUpSubmissionService(
    mockSubscriptionRequestRepository,
    mockEmailRequestRepository,
    mockEmailRequirementService,
    mockCustomerSignUpConnector,
    mockRegistrationConnector,
    mockTaxEnrolmentsConnector,
    mockAuditService
  )

  private implicit val hc: HeaderCarrier = HeaderCarrier()
  private implicit val request = FakeRequest("POST", "testUrl")

  "submitSignUpRequest" when {
    "the user is a delegate and " when {
      val enrolments = Enrolments(Set(testAgentEnrolment))

      "there is a complete company sign up request in storage" when {
        "the e-mail verification request returns that the e-mail address is verified" when {
          "the registration request is successful" when {
            "the sign up request is successful" when {
              "the enrolment call is successful" should {
                "return a SignUpRequestSubmitted for an individual signup" in {
                  val testSubscriptionRequest = SubscriptionRequest(
                    vatNumber = testVatNumber,
                    nino = Some(testNino),
                    ninoSource = Some(UserEntered),
                    email = Some(testEmail)
                  )

                  mockFindById(testVatNumber)(Future.successful(Some(testSubscriptionRequest)))
                  mockCheckRequirements(Some(testEmail), None, isDelegated = true)(Future.successful(Right(Email(testEmail, isVerified = true, shouldSubmit = true))))
                  mockRegisterIndividual(testVatNumber, testNino)(Future.successful(Right(RegisterWithMultipleIdsSuccess(testSafeId))))
                  mockSignUp(testSafeId, testVatNumber, Some(testEmail), emailVerified = Some(true))(Future.successful(Right(CustomerSignUpResponseSuccess)))
                  mockRegisterEnrolment(testVatNumber, testSafeId)(Future.successful(Right(SuccessfulTaxEnrolment)))
                  mockDeleteRecord(testVatNumber)(mock[WriteResult])
                  mockUpsertEmailAfterSubscription(testVatNumber, testEmail, isDelegated = true)(mock[UpdateWriteResult])

                  val res = await(TestSignUpSubmissionService.submitSignUpRequest(testVatNumber, enrolments))

                  res.right.value shouldBe SignUpRequestSubmitted

                  verifyAudit(RegisterWithMultipleIDsAuditModel(TestConstants.testVatNumber, None, Some(TestConstants.testNino),
                    Some(TestConstants.testAgentReferenceNumber), isSuccess = true))
                }
                "return a SignUpRequestSubmitted for a company sign up" in {
                  val testSubscriptionRequest = SubscriptionRequest(
                    vatNumber = testVatNumber,
                    companyNumber = Some(testCompanyNumber),
                    email = Some(testEmail)
                  )

                  mockFindById(testVatNumber)(Future.successful(Some(testSubscriptionRequest)))
                  mockCheckRequirements(Some(testEmail), None, isDelegated = true)(Future.successful(Right(Email(testEmail, isVerified = true, shouldSubmit = true))))
                  mockRegisterCompany(testVatNumber, testCompanyNumber)(Future.successful(Right(RegisterWithMultipleIdsSuccess(testSafeId))))
                  mockSignUp(testSafeId, testVatNumber, Some(testEmail), emailVerified = Some(true))(Future.successful(Right(CustomerSignUpResponseSuccess)))
                  mockRegisterEnrolment(testVatNumber, testSafeId)(Future.successful(Right(SuccessfulTaxEnrolment)))
                  mockDeleteRecord(testVatNumber)(mock[WriteResult])
                  mockUpsertEmailAfterSubscription(testVatNumber, testEmail, isDelegated = true)(mock[UpdateWriteResult])

                  val res = await(TestSignUpSubmissionService.submitSignUpRequest(testVatNumber, enrolments))

                  res.right.value shouldBe SignUpRequestSubmitted

                  verifyAudit(RegisterWithMultipleIDsAuditModel(TestConstants.testVatNumber, Some(TestConstants.testCompanyNumber), None,
                    Some(TestConstants.testAgentReferenceNumber), isSuccess = true))
                  verifyAudit(SignUpAuditModel(TestConstants.testSafeId, TestConstants.testVatNumber, Some(TestConstants.testEmail), Some(true),
                    Some(TestConstants.testAgentReferenceNumber), isSuccess = true))
                }
              }
              "the enrolment call fails" should {
                "return an EnrolmentFailure" in {
                  val testSubscriptionRequest = SubscriptionRequest(
                    vatNumber = testVatNumber,
                    companyNumber = Some(testCompanyNumber),
                    email = Some(testEmail)
                  )

                  mockFindById(testVatNumber)(Future.successful(Some(testSubscriptionRequest)))
                  mockCheckRequirements(Some(testEmail), None, isDelegated = true)(Future.successful(Right(Email(testEmail, isVerified = true, shouldSubmit = true))))
                  mockRegisterCompany(testVatNumber, testCompanyNumber)(Future.successful(Right(RegisterWithMultipleIdsSuccess(testSafeId))))
                  mockSignUp(testSafeId, testVatNumber, Some(testEmail), emailVerified = Some(true))(Future.successful(Right(CustomerSignUpResponseSuccess)))
                  mockRegisterEnrolment(testVatNumber, testSafeId)(Future.successful(Left(FailedTaxEnrolment(BAD_REQUEST))))

                  val res = await(TestSignUpSubmissionService.submitSignUpRequest(testVatNumber, enrolments))

                  res.left.value shouldBe EnrolmentFailure

                  verifyAudit(RegisterWithMultipleIDsAuditModel(TestConstants.testVatNumber, Some(TestConstants.testCompanyNumber), None,
                    Some(TestConstants.testAgentReferenceNumber), isSuccess = true))
                  verifyAudit(SignUpAuditModel(TestConstants.testSafeId, TestConstants.testVatNumber, Some(TestConstants.testEmail), Some(true),
                    Some(TestConstants.testAgentReferenceNumber), isSuccess = true))
                }
              }
            }
            "the sign up request fails" should {
              "return a SignUpFailure" in {
                val testSubscriptionRequest = SubscriptionRequest(
                  vatNumber = testVatNumber,
                  companyNumber = Some(testCompanyNumber),
                  email = Some(testEmail)
                )

                mockFindById(testVatNumber)(Future.successful(Some(testSubscriptionRequest)))
                mockCheckRequirements(Some(testEmail), None, isDelegated = true)(Future.successful(Right(Email(testEmail, isVerified = true, shouldSubmit = true))))
                mockRegisterCompany(testVatNumber, testCompanyNumber)(Future.successful(Right(RegisterWithMultipleIdsSuccess(testSafeId))))
                mockSignUp(testSafeId, testVatNumber, Some(testEmail), emailVerified = Some(true))(Future.successful(Left(CustomerSignUpResponseFailure(BAD_REQUEST))))

                val res = await(TestSignUpSubmissionService.submitSignUpRequest(testVatNumber, enrolments))

                res.left.value shouldBe SignUpFailure

                verifyAudit(RegisterWithMultipleIDsAuditModel(TestConstants.testVatNumber, Some(TestConstants.testCompanyNumber), None,
                  Some(TestConstants.testAgentReferenceNumber), isSuccess = true))
                verifyAudit(SignUpAuditModel(TestConstants.testSafeId, TestConstants.testVatNumber, Some(TestConstants.testEmail), Some(true),
                  Some(TestConstants.testAgentReferenceNumber), isSuccess = false))
              }
            }
          }
          "the registration request fails" should {
            "return a RegistrationFailure" in {
              val testSubscriptionRequest = SubscriptionRequest(
                vatNumber = testVatNumber,
                companyNumber = Some(testCompanyNumber),
                email = Some(testEmail)
              )

              mockFindById(testVatNumber)(
                Future.successful(Some(testSubscriptionRequest))
              )
              mockCheckRequirements(Some(testEmail), None, isDelegated = true)(Future.successful(Right(Email(testEmail, isVerified = true, shouldSubmit = true))))
              mockRegisterCompany(testVatNumber, testCompanyNumber)(
                Future.successful(Left(RegisterWithMultipleIdsErrorResponse(BAD_REQUEST, "")))
              )

              val res = await(TestSignUpSubmissionService.submitSignUpRequest(testVatNumber, enrolments))

              res.left.value shouldBe RegistrationFailure

              verifyAudit(RegisterWithMultipleIDsAuditModel(TestConstants.testVatNumber, Some(TestConstants.testCompanyNumber), None,
                Some(TestConstants.testAgentReferenceNumber), isSuccess = false))
            }
          }
        }
        "transaction email is not present and principal email is not verified" when {
          "the registration request is successful" when {
            "the sign up request is successful" when {
              "the enrolment call is successful" should {
                "return a SignUpRequestSubmitted" in {
                  val testSubscriptionRequest = SubscriptionRequest(
                    vatNumber = testVatNumber,
                    companyNumber = Some(testCompanyNumber),
                    email = Some(testEmail)
                  )

                  mockFindById(testVatNumber)(Future.successful(Some(testSubscriptionRequest)))
                  mockCheckRequirements(Some(testEmail), None, isDelegated = true)(Future.successful(Right(Email(testEmail, isVerified = false, shouldSubmit = true))))
                  mockRegisterCompany(testVatNumber, testCompanyNumber)(Future.successful(Right(RegisterWithMultipleIdsSuccess(testSafeId))))
                  mockSignUp(testSafeId, testVatNumber, Some(testEmail), emailVerified = Some(false))(Future.successful(Right(CustomerSignUpResponseSuccess)))
                  mockRegisterEnrolment(testVatNumber, testSafeId)(Future.successful(Right(SuccessfulTaxEnrolment)))
                  mockDeleteRecord(testVatNumber)(mock[WriteResult])
                  mockUpsertEmailAfterSubscription(testVatNumber, testEmail, isDelegated = true)(mock[UpdateWriteResult])

                  val res = await(TestSignUpSubmissionService.submitSignUpRequest(testVatNumber, enrolments))

                  res.right.value shouldBe SignUpRequestSubmitted

                  verifyAudit(RegisterWithMultipleIDsAuditModel(TestConstants.testVatNumber, Some(TestConstants.testCompanyNumber), None,
                    Some(TestConstants.testAgentReferenceNumber), isSuccess = true))
                  verifyAudit(SignUpAuditModel(TestConstants.testSafeId, TestConstants.testVatNumber, Some(TestConstants.testEmail), Some(false),
                    Some(TestConstants.testAgentReferenceNumber), isSuccess = true))
                }
              }
            }
          }
        }
        "transaction email is present and transaction email is verified" when {
          "the registration request is successful" when {
            "the sign up request is successful" when {
              "the enrolment call is successful" should {
                "not submit the email when calling sign up" in {
                  val testSubscriptionRequest = SubscriptionRequest(
                    vatNumber = testVatNumber,
                    companyNumber = Some(testCompanyNumber),
                    transactionEmail = Some(testEmail)
                  )

                  mockFindById(testVatNumber)(Future.successful(Some(testSubscriptionRequest)))
                  mockCheckRequirements(None, Some(testEmail), isDelegated = true)(Future.successful(Right(Email(testEmail, isVerified = true, shouldSubmit = false))))
                  mockRegisterCompany(testVatNumber, testCompanyNumber)(Future.successful(Right(RegisterWithMultipleIdsSuccess(testSafeId))))
                  mockSignUp(testSafeId, testVatNumber, None, emailVerified = None)(Future.successful(Right(CustomerSignUpResponseSuccess)))
                  mockRegisterEnrolment(testVatNumber, testSafeId)(Future.successful(Right(SuccessfulTaxEnrolment)))
                  mockDeleteRecord(testVatNumber)(mock[WriteResult])
                  mockUpsertEmailAfterSubscription(testVatNumber, testEmail, isDelegated = true)(mock[UpdateWriteResult])

                  val res = await(TestSignUpSubmissionService.submitSignUpRequest(testVatNumber, enrolments))

                  res.right.value shouldBe SignUpRequestSubmitted

                  verifyAudit(RegisterWithMultipleIDsAuditModel(TestConstants.testVatNumber, Some(TestConstants.testCompanyNumber), None,
                    Some(TestConstants.testAgentReferenceNumber), isSuccess = true))
                  verifyAudit(SignUpAuditModel(TestConstants.testSafeId, TestConstants.testVatNumber, None, None,
                    Some(TestConstants.testAgentReferenceNumber), isSuccess = true))
                }
              }
            }
          }
        }
        "the email verification request fails" should {
          "return an EmailVerificationFailure" in {
            val testSubscriptionRequest = SubscriptionRequest(
              vatNumber = testVatNumber,
              companyNumber = Some(testCompanyNumber),
              email = Some(testEmail)
            )

            mockFindById(testVatNumber)(
              Future.successful(Some(testSubscriptionRequest))
            )

            mockCheckRequirements(Some(testEmail), None, isDelegated = true)(Future.successful(Left(GetEmailVerificationFailure)))

            val res = await(TestSignUpSubmissionService.submitSignUpRequest(testVatNumber, enrolments))

            res.left.value shouldBe EmailVerificationFailure
          }
        }

      }
      "there is insufficient data in storage" should {
        "return an InsufficientData error" in {
          val incompleteSubscriptionRequest = SubscriptionRequest(
            vatNumber = testVatNumber,
            companyNumber = None,
            email = Some(testEmail)
          )

          mockFindById(testVatNumber)(
            Future.successful(Some(incompleteSubscriptionRequest))
          )

          val res = await(TestSignUpSubmissionService.submitSignUpRequest(testVatNumber, enrolments))

          res.left.value shouldBe InsufficientData
        }
      }

      "there is no data in storage" should {
        "return an InsufficientData error" in {
          mockFindById(testVatNumber)(
            Future.successful(None)
          )

          val res = await(TestSignUpSubmissionService.submitSignUpRequest(testVatNumber, enrolments))

          res.left.value shouldBe InsufficientData
        }
      }
    }

    "the user is principal and " when {
      val enrolments = Enrolments(Set(testPrincipalEnrolment))

      "there is a complete company sign up request in storage" when {
        "the e-mail verification request returns that the e-mail address is verified" when {
          "the registration request is successful" when {
            "the sign up request is successful" when {
              "the enrolment call is successful" should {
                "return a SignUpRequestSubmitted for an individual signup" in {
                  val testSubscriptionRequest = SubscriptionRequest(
                    vatNumber = testVatNumber,
                    nino = Some(testNino),
                    ninoSource = Some(UserEntered),
                    email = Some(testEmail),
                    identityVerified = true
                  )

                  mockFindById(testVatNumber)(Future.successful(Some(testSubscriptionRequest)))
                  mockCheckRequirements(Some(testEmail), None, isDelegated = false)(Future.successful(Right(Email(testEmail, isVerified = true, shouldSubmit = true))))
                  mockRegisterIndividual(testVatNumber, testNino)(Future.successful(Right(RegisterWithMultipleIdsSuccess(testSafeId))))
                  mockSignUp(testSafeId, testVatNumber, Some(testEmail), emailVerified = Some(true))(Future.successful(Right(CustomerSignUpResponseSuccess)))
                  mockRegisterEnrolment(testVatNumber, testSafeId)(Future.successful(Right(SuccessfulTaxEnrolment)))
                  mockDeleteRecord(testVatNumber)(mock[WriteResult])
                  mockUpsertEmailAfterSubscription(testVatNumber, testEmail, isDelegated = false)(mock[UpdateWriteResult])

                  val res = await(TestSignUpSubmissionService.submitSignUpRequest(testVatNumber, enrolments))

                  res.right.value shouldBe SignUpRequestSubmitted

                  verifyAudit(RegisterWithMultipleIDsAuditModel(TestConstants.testVatNumber, None, Some(TestConstants.testNino), None, isSuccess = true))
                  verifyAudit(SignUpAuditModel(TestConstants.testSafeId, TestConstants.testVatNumber, Some(TestConstants.testEmail), Some(true), None, isSuccess = true))
                }
                "return a SignUpRequestSubmitted for a company sign up" in {
                  val testSubscriptionRequest = SubscriptionRequest(
                    vatNumber = testVatNumber,
                    companyNumber = Some(testCompanyNumber),
                    email = Some(testEmail),
                    identityVerified = true
                  )

                  mockFindById(testVatNumber)(Future.successful(Some(testSubscriptionRequest)))
                  mockCheckRequirements(Some(testEmail), None, isDelegated = false)(Future.successful(Right(Email(testEmail, isVerified = true, shouldSubmit = true))))
                  mockRegisterCompany(testVatNumber, testCompanyNumber)(Future.successful(Right(RegisterWithMultipleIdsSuccess(testSafeId))))
                  mockSignUp(testSafeId, testVatNumber, Some(testEmail), emailVerified = Some(true))(Future.successful(Right(CustomerSignUpResponseSuccess)))
                  mockRegisterEnrolment(testVatNumber, testSafeId)(Future.successful(Right(SuccessfulTaxEnrolment)))
                  mockDeleteRecord(testVatNumber)(mock[WriteResult])
                  mockUpsertEmailAfterSubscription(testVatNumber, testEmail, isDelegated = false)(mock[UpdateWriteResult])

                  val res = await(TestSignUpSubmissionService.submitSignUpRequest(testVatNumber, enrolments))

                  res.right.value shouldBe SignUpRequestSubmitted

                  verifyAudit(RegisterWithMultipleIDsAuditModel(TestConstants.testVatNumber, Some(TestConstants.testCompanyNumber), None, None, isSuccess = true))
                  verifyAudit(SignUpAuditModel(TestConstants.testSafeId, TestConstants.testVatNumber, Some(TestConstants.testEmail), Some(true), None, isSuccess = true))
                }
              }
              "the enrolment call fails" should {
                "return an EnrolmentFailure" in {
                  val testSubscriptionRequest = SubscriptionRequest(
                    vatNumber = testVatNumber,
                    companyNumber = Some(testCompanyNumber),
                    email = Some(testEmail),
                    identityVerified = true
                  )

                  mockFindById(testVatNumber)(Future.successful(Some(testSubscriptionRequest)))
                  mockCheckRequirements(Some(testEmail), None, isDelegated = false)(Future.successful(Right(Email(testEmail, isVerified = true, shouldSubmit = true))))
                  mockRegisterCompany(testVatNumber, testCompanyNumber)(Future.successful(Right(RegisterWithMultipleIdsSuccess(testSafeId))))
                  mockSignUp(testSafeId, testVatNumber, Some(testEmail), emailVerified = Some(true))(Future.successful(Right(CustomerSignUpResponseSuccess)))
                  mockRegisterEnrolment(testVatNumber, testSafeId)(Future.successful(Left(FailedTaxEnrolment(BAD_REQUEST))))

                  val res = await(TestSignUpSubmissionService.submitSignUpRequest(testVatNumber, enrolments))

                  res.left.value shouldBe EnrolmentFailure

                  verifyAudit(RegisterWithMultipleIDsAuditModel(TestConstants.testVatNumber, Some(TestConstants.testCompanyNumber), None, None, isSuccess = true))
                  verifyAudit(SignUpAuditModel(TestConstants.testSafeId, TestConstants.testVatNumber, Some(TestConstants.testEmail), Some(true), None, isSuccess = true))
                }
              }
            }
            "the sign up request fails" should {
              "return a SignUpFailure" in {
                val testSubscriptionRequest = SubscriptionRequest(
                  vatNumber = testVatNumber,
                  companyNumber = Some(testCompanyNumber),
                  email = Some(testEmail),
                  identityVerified = true
                )

                mockFindById(testVatNumber)(Future.successful(Some(testSubscriptionRequest)))
                mockCheckRequirements(Some(testEmail), None, isDelegated = false)(Future.successful(Right(Email(testEmail, isVerified = true, shouldSubmit = true))))
                mockRegisterCompany(testVatNumber, testCompanyNumber)(Future.successful(Right(RegisterWithMultipleIdsSuccess(testSafeId))))
                mockSignUp(testSafeId, testVatNumber, Some(testEmail), emailVerified = Some(true))(Future.successful(Left(CustomerSignUpResponseFailure(BAD_REQUEST))))

                val res = await(TestSignUpSubmissionService.submitSignUpRequest(testVatNumber, enrolments))

                res.left.value shouldBe SignUpFailure

                verifyAudit(RegisterWithMultipleIDsAuditModel(TestConstants.testVatNumber, Some(TestConstants.testCompanyNumber), None, None, isSuccess = true))
                verifyAudit(SignUpAuditModel(TestConstants.testSafeId, TestConstants.testVatNumber, Some(TestConstants.testEmail), Some(true), None, isSuccess = false))
              }
            }
          }
          "the registration request fails" should {
            "return a RegistrationFailure" in {
              val testSubscriptionRequest = SubscriptionRequest(
                vatNumber = testVatNumber,
                companyNumber = Some(testCompanyNumber),
                email = Some(testEmail),
                identityVerified = true
              )

              mockFindById(testVatNumber)(
                Future.successful(Some(testSubscriptionRequest))
              )
              mockCheckRequirements(Some(testEmail), None, isDelegated = false)(Future.successful(Right(Email(testEmail, isVerified = true, shouldSubmit = true))))
              mockRegisterCompany(testVatNumber, testCompanyNumber)(
                Future.successful(Left(RegisterWithMultipleIdsErrorResponse(BAD_REQUEST, "")))
              )

              val res = await(TestSignUpSubmissionService.submitSignUpRequest(testVatNumber, enrolments))

              res.left.value shouldBe RegistrationFailure

              verifyAudit(RegisterWithMultipleIDsAuditModel(TestConstants.testVatNumber, Some(TestConstants.testCompanyNumber), None, None, isSuccess = false))
            }
          }
        }
        "the email verification request returns that the email is not verified" should {
          "return a EmailVerificationRequired" in {
            val testSubscriptionRequest = SubscriptionRequest(
              vatNumber = testVatNumber,
              companyNumber = Some(testCompanyNumber),
              email = Some(testEmail),
              identityVerified = true
            )

            mockFindById(testVatNumber)(Future.successful(Some(testSubscriptionRequest)))
            mockCheckRequirements(Some(testEmail), None, isDelegated = false)(Future.successful(Left(UnVerifiedEmail)))

            val res = await(TestSignUpSubmissionService.submitSignUpRequest(testVatNumber, enrolments))

            res.left.value shouldBe EmailVerificationRequired
          }
        }
        "the email verification request fails" should {
          "return an EmailVerificationFailure" in {
            val testSubscriptionRequest = SubscriptionRequest(
              vatNumber = testVatNumber,
              companyNumber = Some(testCompanyNumber),
              email = Some(testEmail),
              identityVerified = true
            )

            mockFindById(testVatNumber)(
              Future.successful(Some(testSubscriptionRequest))
            )
            mockCheckRequirements(Some(testEmail), None, isDelegated = false)(Future.successful(Left(GetEmailVerificationFailure)))

            val res = await(TestSignUpSubmissionService.submitSignUpRequest(testVatNumber, enrolments))

            res.left.value shouldBe EmailVerificationFailure
          }
        }

      }
      "there is insufficient data in storage" should {
        "return an InsufficientData error" in {
          val incompleteSubscriptionRequest = SubscriptionRequest(
            vatNumber = testVatNumber,
            companyNumber = None,
            email = Some(testEmail),
            identityVerified = true
          )

          mockFindById(testVatNumber)(
            Future.successful(Some(incompleteSubscriptionRequest))
          )

          val res = await(TestSignUpSubmissionService.submitSignUpRequest(testVatNumber, enrolments))

          res.left.value shouldBe InsufficientData
        }
      }

      "identity not verified" when {
        "the entity is company" should {
          "return an InsufficientData error" in {
            val incompleteSubscriptionRequest = SubscriptionRequest(
              vatNumber = testVatNumber,
              companyNumber = Some(testCompanyNumber),
              email = Some(testEmail),
              identityVerified = false
            )

            mockFindById(testVatNumber)(
              Future.successful(Some(incompleteSubscriptionRequest))
            )

            val res = await(TestSignUpSubmissionService.submitSignUpRequest(testVatNumber, enrolments))

            res.left.value shouldBe InsufficientData
          }
        }
        "the entity is individual and" when {
          "the nino is user entered" should {
            "return an InsufficientData error" in {
              val incompleteSubscriptionRequest = SubscriptionRequest(
                vatNumber = testVatNumber,
                nino = Some(testNino),
                ninoSource = Some(UserEntered),
                email = Some(testEmail),
                identityVerified = false
              )

              mockFindById(testVatNumber)(
                Future.successful(Some(incompleteSubscriptionRequest))
              )

              val res = await(TestSignUpSubmissionService.submitSignUpRequest(testVatNumber, enrolments))

              res.left.value shouldBe InsufficientData
            }
          }
          "the nino is retrieved via IR-SA" should {
            "proceed to sign up normally" in {
              val completeSubscriptionRequest = SubscriptionRequest(
                vatNumber = testVatNumber,
                nino = Some(testNino),
                ninoSource = Some(IRSA),
                email = Some(testEmail),
                identityVerified = false
              )

              mockFindById(testVatNumber)(
                Future.successful(Some(completeSubscriptionRequest))
              )
              mockCheckRequirements(Some(testEmail), None, isDelegated = false)(Future.successful(Right(Email(testEmail, isVerified = true, shouldSubmit = true))))
              mockRegisterIndividual(testVatNumber, testNino)(Future.successful(Right(RegisterWithMultipleIdsSuccess(testSafeId))))
              mockSignUp(testSafeId, testVatNumber, Some(testEmail), emailVerified = Some(true))(Future.successful(Right(CustomerSignUpResponseSuccess)))
              mockRegisterEnrolment(testVatNumber, testSafeId)(Future.successful(Right(SuccessfulTaxEnrolment)))
              mockDeleteRecord(testVatNumber)(mock[WriteResult])
              mockUpsertEmailAfterSubscription(testVatNumber, testEmail, isDelegated = false)(mock[UpdateWriteResult])

              val res = await(TestSignUpSubmissionService.submitSignUpRequest(testVatNumber, enrolments))

              res.right.value shouldBe SignUpRequestSubmitted
            }
          }
        }
      }

      "there is no data in storage" should {
        "return an InsufficientData error" in {
          mockFindById(testVatNumber)(
            Future.successful(None)
          )

          val res = await(TestSignUpSubmissionService.submitSignUpRequest(testVatNumber, enrolments))

          res.left.value shouldBe InsufficientData
        }
      }
    }
  }
}