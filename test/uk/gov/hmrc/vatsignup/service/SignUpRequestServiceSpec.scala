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

import play.api.http.Status
import uk.gov.hmrc.auth.core.Enrolments
import uk.gov.hmrc.http.HeaderCarrier
import uk.gov.hmrc.play.test.UnitSpec
import uk.gov.hmrc.vatsignup.connectors.mocks.MockEmailVerificationConnector
import uk.gov.hmrc.vatsignup.helpers.TestConstants._
import uk.gov.hmrc.vatsignup.httpparsers.GetEmailVerificationStateHttpParser
import uk.gov.hmrc.vatsignup.httpparsers.GetEmailVerificationStateHttpParser.{EmailNotVerified, EmailVerified}
import uk.gov.hmrc.vatsignup.models.SignUpRequest.{EmailAddress, LimitedCompany, SoleTrader}
import uk.gov.hmrc.vatsignup.models.{IRSA, SignUpRequest, SubscriptionRequest, UserEntered}
import uk.gov.hmrc.vatsignup.repositories.mocks.MockSubscriptionRequestRepository
import uk.gov.hmrc.vatsignup.services.SignUpRequestService
import uk.gov.hmrc.vatsignup.services.SignUpRequestService.{EmailVerificationFailure, EmailVerificationRequired, InsufficientData, RequestNotAuthorised}

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

class SignUpRequestServiceSpec extends UnitSpec
  with MockSubscriptionRequestRepository with MockEmailVerificationConnector {

  object TestSignUpRequestService extends SignUpRequestService(
    mockSubscriptionRequestRepository,
    mockEmailVerificationConnector
  )

  implicit val hc: HeaderCarrier = HeaderCarrier()

  "getSignUpRequest" when {
    "the request is not at delegated" when {
      "there is a stored company number" when {
        "there is a stored CT reference" when {
          "there is a stored sign up email address" when {
            "the sign up email address is verified" when {
              "there is not a transaction e-mail address" should {
                "return a successful SignUpRequest" in {
                  val testSubscriptionRequest =
                    SubscriptionRequest(
                      vatNumber = testVatNumber,
                      companyNumber = Some(testCompanyNumber),
                      ctReference = Some(testCtReference),
                      email = Some(testEmail)
                    )

                  mockFindById(testVatNumber)(Future.successful(Some(testSubscriptionRequest)))
                  mockGetEmailVerificationState(testEmail)(Future.successful(Right(EmailVerified)))

                  val res = TestSignUpRequestService.getSignUpRequest(testVatNumber, Enrolments(Set.empty))

                  val verifiedEmail = EmailAddress(testEmail, isVerified = true)

                  await(res) shouldBe Right(
                    SignUpRequest(
                      vatNumber = testVatNumber,
                      businessEntity = LimitedCompany(testCompanyNumber),
                      signUpEmail = Some(verifiedEmail),
                      transactionEmail = verifiedEmail,
                      isDelegated = false
                    )
                  )
                }
              }
            }
            "the sign up email address is not at verified" should {
              "return EmailVerificationRequired" in {
                val testSubscriptionRequest =
                  SubscriptionRequest(
                    vatNumber = testVatNumber,
                    companyNumber = Some(testCompanyNumber),
                    ctReference = Some(testCtReference),
                    email = Some(testEmail)
                  )

                mockFindById(testVatNumber)(Future.successful(Some(testSubscriptionRequest)))
                mockGetEmailVerificationState(testEmail)(Future.successful(Right(EmailNotVerified)))

                val res = TestSignUpRequestService.getSignUpRequest(testVatNumber, Enrolments(Set.empty))

                await(res) shouldBe Left(EmailVerificationRequired)
              }
            }
            "the call to verify e-mail fails" should {
              "return EmailVerificationFailure" in {
                val testSubscriptionRequest =
                  SubscriptionRequest(
                    vatNumber = testVatNumber,
                    companyNumber = Some(testCompanyNumber),
                    ctReference = Some(testCtReference),
                    email = Some(testEmail)
                  )

                mockFindById(testVatNumber)(Future.successful(Some(testSubscriptionRequest)))
                mockGetEmailVerificationState(testEmail)(
                  Future.successful(Left(GetEmailVerificationStateHttpParser.GetEmailVerificationStateErrorResponse(Status.INTERNAL_SERVER_ERROR, "")))
                )

                val res = TestSignUpRequestService.getSignUpRequest(testVatNumber, Enrolments(Set.empty))

                await(res) shouldBe Left(EmailVerificationFailure)
              }
            }
          }
          "there is not a stored email address" should {
            "return InsufficientData" in {
              val testSubscriptionRequest =
                SubscriptionRequest(
                  vatNumber = testVatNumber,
                  companyNumber = Some(testCompanyNumber),
                  ctReference = Some(testCtReference)
                )

              mockFindById(testVatNumber)(Future.successful(Some(testSubscriptionRequest)))

              val res = TestSignUpRequestService.getSignUpRequest(testVatNumber, Enrolments(Set.empty))

              await(res) shouldBe Left(InsufficientData)
            }
          }
        }
        "there is not a stored CT reference" when {
          "there is a stored identity verified flag" when {
            "there is a stored sign up email address" when {
              "the sign up email address is verified" when {
                "there is not a transaction e-mail address" should {
                  "return a successful SignUpRequest" in {
                    val testSubscriptionRequest =
                      SubscriptionRequest(
                        vatNumber = testVatNumber,
                        companyNumber = Some(testCompanyNumber),
                        email = Some(testEmail),
                        identityVerified = true
                      )

                    mockFindById(testVatNumber)(Future.successful(Some(testSubscriptionRequest)))
                    mockGetEmailVerificationState(testEmail)(Future.successful(Right(EmailVerified)))

                    val res = TestSignUpRequestService.getSignUpRequest(testVatNumber, Enrolments(Set.empty))

                    val verifiedEmail = EmailAddress(testEmail, isVerified = true)

                    await(res) shouldBe Right(
                      SignUpRequest(
                        vatNumber = testVatNumber,
                        businessEntity = LimitedCompany(testCompanyNumber),
                        signUpEmail = Some(verifiedEmail),
                        transactionEmail = verifiedEmail,
                        isDelegated = false
                      )
                    )
                  }
                }
              }
            }
          }
          "there is not a stored identity verified flag" should {
            "return RequestNotAuthorised" in {
              val testSubscriptionRequest =
                SubscriptionRequest(
                  vatNumber = testVatNumber,
                  companyNumber = Some(testCompanyNumber),
                  email = Some(testEmail)
                )

              mockFindById(testVatNumber)(Future.successful(Some(testSubscriptionRequest)))

              val res = TestSignUpRequestService.getSignUpRequest(testVatNumber, Enrolments(Set.empty))

              await(res) shouldBe Left(RequestNotAuthorised)
            }
          }
        }
      }
      "there is a stored NINO" when {
        "the NINO source is IR-SA" when {
          "there is a stored sign up email address" when {
            "the sign up email address is verified" when {
              "there is not a transaction e-mail address" should {
                "return a successful SignUpRequest" in {
                  val testSubscriptionRequest =
                    SubscriptionRequest(
                      vatNumber = testVatNumber,
                      nino = Some(testNino),
                      ninoSource = Some(IRSA),
                      email = Some(testEmail)
                    )

                  mockFindById(testVatNumber)(Future.successful(Some(testSubscriptionRequest)))
                  mockGetEmailVerificationState(testEmail)(Future.successful(Right(EmailVerified)))

                  val res = TestSignUpRequestService.getSignUpRequest(testVatNumber, Enrolments(Set.empty))

                  val verifiedEmail = EmailAddress(testEmail, isVerified = true)

                  await(res) shouldBe Right(
                    SignUpRequest(
                      vatNumber = testVatNumber,
                      businessEntity = SoleTrader(testNino),
                      signUpEmail = Some(verifiedEmail),
                      transactionEmail = verifiedEmail,
                      isDelegated = false
                    )
                  )
                }
              }
            }
          }
        }
        "the NINO source is UserEntered" when {
          "there is a stored identity verified flag" when {
            "there is a stored sign up email address" when {
              "the sign up email address is verified" when {
                "there is not a transaction e-mail address" should {
                  "return a successful SignUpRequest" in {
                    val testSubscriptionRequest =
                      SubscriptionRequest(
                        vatNumber = testVatNumber,
                        nino = Some(testNino),
                        ninoSource = Some(UserEntered),
                        email = Some(testEmail),
                        identityVerified = true
                      )

                    mockFindById(testVatNumber)(Future.successful(Some(testSubscriptionRequest)))
                    mockGetEmailVerificationState(testEmail)(Future.successful(Right(EmailVerified)))

                    val res = TestSignUpRequestService.getSignUpRequest(testVatNumber, Enrolments(Set.empty))

                    val verifiedEmail = EmailAddress(testEmail, isVerified = true)

                    await(res) shouldBe Right(
                      SignUpRequest(
                        vatNumber = testVatNumber,
                        businessEntity = SoleTrader(testNino),
                        signUpEmail = Some(verifiedEmail),
                        transactionEmail = verifiedEmail,
                        isDelegated = false
                      )
                    )
                  }
                }
              }
            }
          }
          "there is not a identity verified flag" should {
            "return RequestNotAuthorised" in {
              val testSubscriptionRequest =
                SubscriptionRequest(
                  vatNumber = testVatNumber,
                  nino = Some(testNino),
                  ninoSource = Some(UserEntered),
                  email = Some(testEmail)
                )

              mockFindById(testVatNumber)(Future.successful(Some(testSubscriptionRequest)))

              val res = TestSignUpRequestService.getSignUpRequest(testVatNumber, Enrolments(Set.empty))

              await(res) shouldBe Left(RequestNotAuthorised)
            }
          }
        }
      }
      "there is not a stored NINO or company number" should {
        "return InsufficientData" in {
          val testSubscriptionRequest =
            SubscriptionRequest(
              vatNumber = testVatNumber,
              email = Some(testEmail)
            )

          mockFindById(testVatNumber)(Future.successful(Some(testSubscriptionRequest)))

          val res = TestSignUpRequestService.getSignUpRequest(testVatNumber, Enrolments(Set.empty))

          await(res) shouldBe Left(InsufficientData)
        }
      }
    }
    "the request is delegated" when {
      "there is a stored NINO" when {
        "there is an unverified sign up e-mail" should {
          "return a successful SignUpRequest" in {
            val testSubscriptionRequest =
              SubscriptionRequest(
                vatNumber = testVatNumber,
                nino = Some(testNino),
                email = Some(testEmail)
              )

            mockFindById(testVatNumber)(Future.successful(Some(testSubscriptionRequest)))
            mockGetEmailVerificationState(testEmail)(Future.successful(Right(EmailNotVerified)))

            val res = TestSignUpRequestService.getSignUpRequest(testVatNumber, Enrolments(Set(testAgentEnrolment)))

            val unverifiedEmail = EmailAddress(testEmail, isVerified = false)

            await(res) shouldBe Right(
              SignUpRequest(
                vatNumber = testVatNumber,
                businessEntity = SoleTrader(testNino),
                signUpEmail = Some(unverifiedEmail),
                transactionEmail = unverifiedEmail,
                isDelegated = true
              )
            )
          }
        }
        "there is not a sign up e-mail" when {
          "there is a verified transaction e-mail" should {
            "return a successful SignUpRequest" in {
              val testSubscriptionRequest =
                SubscriptionRequest(
                  vatNumber = testVatNumber,
                  nino = Some(testNino),
                  transactionEmail = Some(testEmail)
                )

              mockFindById(testVatNumber)(Future.successful(Some(testSubscriptionRequest)))
              mockGetEmailVerificationState(testEmail)(Future.successful(Right(EmailVerified)))

              val res = TestSignUpRequestService.getSignUpRequest(testVatNumber, Enrolments(Set(testAgentEnrolment)))

              val verifiedEmail = EmailAddress(testEmail, isVerified = true)

              await(res) shouldBe Right(
                SignUpRequest(
                  vatNumber = testVatNumber,
                  businessEntity = SoleTrader(testNino),
                  signUpEmail = None,
                  transactionEmail = verifiedEmail,
                  isDelegated = true
                )
              )
            }
          }
          "there is an unverified transaction e-mail" should {
            "return EmailVerificationRequired" in {
              val testSubscriptionRequest =
                SubscriptionRequest(
                  vatNumber = testVatNumber,
                  nino = Some(testNino),
                  transactionEmail = Some(testEmail)
                )

              mockFindById(testVatNumber)(Future.successful(Some(testSubscriptionRequest)))
              mockGetEmailVerificationState(testEmail)(Future.successful(Right(EmailNotVerified)))

              val res = TestSignUpRequestService.getSignUpRequest(testVatNumber, Enrolments(Set(testAgentEnrolment)))

              await(res) shouldBe Left(EmailVerificationRequired)
            }
          }
          "there is not a transaction e-mail" should {
            "return InsufficientData" in {
              val testSubscriptionRequest =
                SubscriptionRequest(
                  vatNumber = testVatNumber,
                  nino = Some(testNino)
                )

              mockFindById(testVatNumber)(Future.successful(Some(testSubscriptionRequest)))

              val res = TestSignUpRequestService.getSignUpRequest(testVatNumber, Enrolments(Set(testAgentEnrolment)))

              await(res) shouldBe Left(InsufficientData)
            }
          }
        }
      }
    }
  }
}
