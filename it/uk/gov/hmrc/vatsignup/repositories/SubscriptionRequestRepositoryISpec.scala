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

package uk.gov.hmrc.vatsignup.repositories

import java.util.UUID

import org.scalatest.BeforeAndAfterEach
import org.scalatestplus.play.guice.GuiceOneAppPerSuite
import uk.gov.hmrc.play.test.UnitSpec
import uk.gov.hmrc.vatsignup.helpers.IntegrationTestConstants._
import uk.gov.hmrc.vatsignup.models.{SubscriptionRequest, UserEntered}

import scala.concurrent.ExecutionContext.Implicits.global

class SubscriptionRequestRepositoryISpec extends UnitSpec with GuiceOneAppPerSuite with BeforeAndAfterEach {
  val repo: SubscriptionRequestRepository = app.injector.instanceOf[SubscriptionRequestRepository]

  private val testSubscriptionRequest = SubscriptionRequest(
    vatNumber = testVatNumber,
    companyNumber = Some(testCompanyNumber)
  )

  override def beforeEach: Unit = {
    super.beforeEach()
    await(repo.drop)
  }

  "insert" should {
    "successfully insert and retrieve a SubscriptionRequest model" in {
      val res = for {
        _ <- repo.insert(testSubscriptionRequest)
        model <- repo.findById(testVatNumber)
      } yield model

      await(res) should contain(testSubscriptionRequest)
    }
  }

  "insertVatNumber" should {
    val testSubscriptionRequest = SubscriptionRequest(
      vatNumber = testVatNumber
    )

    "insert the subscription request where there is not already one" in {
      val res = for {
        _ <- repo.upsertVatNumber(testVatNumber)
        model <- repo.findById(testVatNumber)
      } yield model

      await(res) should contain(testSubscriptionRequest)
    }

    "replace the previous data when one already exists" in {
      val res = for {
        _ <- repo.insert(SubscriptionRequest(testVatNumber, Some(testCompanyNumber), Some(testCtReference), Some(testNino), Some(UserEntered), Some(testEmail), identityVerified = true))
        _ <- repo.upsertVatNumber(testVatNumber)
        model <- repo.findById(testVatNumber)
      } yield model

      await(res) should contain(testSubscriptionRequest)

    }
  }


  "upsertCompanyNumber" should {
    val testSubscriptionRequest = SubscriptionRequest(
      vatNumber = testVatNumber,
      companyNumber = Some(testCompanyNumber)
    )

    "throw NoSuchElementException where the vat number doesn't exist" in {
      val res = for {
        _ <- repo.upsertCompanyNumber(testVatNumber, testCompanyNumber)
        model <- repo.findById(testVatNumber)
      } yield model

      intercept[NoSuchElementException] {
        await(res)
      }
    }

    "update the subscription request where there isn't already a company number stored" in {
      val res = for {
        _ <- repo.upsertVatNumber(testVatNumber)
        _ <- repo.upsertCompanyNumber(testVatNumber, testCompanyNumber)
        model <- repo.findById(testVatNumber)
      } yield model

      await(res) should contain(testSubscriptionRequest)
    }

    "delete the nino if it already exists" in {
      val res = for {
        _ <- repo.upsertVatNumber(testVatNumber)
        _ <- repo.upsertNino(testVatNumber, testNino, UserEntered)
        withNino <- repo.findById(testVatNumber)
        _ <- repo.upsertCompanyNumber(testVatNumber, testCompanyNumber)
        withoutNino <- repo.findById(testVatNumber)
      } yield (withNino, withoutNino)

      val (withNino, withoutNino) = await(res)

      withNino should contain(testSubscriptionRequest.copy(nino = Some(testNino), ninoSource = Some(UserEntered), companyNumber = None))
      withoutNino should contain(testSubscriptionRequest)
    }

    "replace an existing stored company number" in {
      val newCompanyNumber = UUID.randomUUID().toString
      val res = for {
        _ <- repo.insert(testSubscriptionRequest)
        _ <- repo.upsertCompanyNumber(testVatNumber, newCompanyNumber)
        model <- repo.findById(testVatNumber)
      } yield model

      await(res) should contain(SubscriptionRequest(testVatNumber, companyNumber = Some(newCompanyNumber)))
    }
  }

  "upsertEmail" should {
    val testSubscriptionRequest = SubscriptionRequest(
      vatNumber = testVatNumber,
      email = Some(testEmail)
    )

    "throw NoSuchElementException where the vat number doesn't exist" in {
      val res = for {
        _ <- repo.upsertEmail(testVatNumber, testEmail)
        model <- repo.findById(testVatNumber)
      } yield model

      intercept[NoSuchElementException] {
        await(res)
      }
    }

    "update the subscription request where there isn't already an email stored" in {
      val res = for {
        _ <- repo.upsertVatNumber(testVatNumber)
        _ <- repo.upsertEmail(testVatNumber, testEmail)
        model <- repo.findById(testVatNumber)
      } yield model

      await(res) should contain(testSubscriptionRequest)
    }

    "replace an existing stored email" in {
      val newEmail = UUID.randomUUID().toString
      val res = for {
        _ <- repo.insert(testSubscriptionRequest)
        _ <- repo.upsertEmail(testVatNumber, newEmail)
        model <- repo.findById(testVatNumber)
      } yield model

      await(res) should contain(SubscriptionRequest(testVatNumber, email = Some(newEmail)))
    }
  }

  "upsertTransactionEmail" should {
    val testSubscriptionRequest = SubscriptionRequest(
      vatNumber = testVatNumber,
      transactionEmail = Some(testEmail)
    )

    "throw NoSuchElementException where the vat number doesn't exist" in {
      val res = for {
        _ <- repo.upsertTransactionEmail(testVatNumber, testEmail)
        model <- repo.findById(testVatNumber)
      } yield model

      intercept[NoSuchElementException] {
        await(res)
      }
    }

    "update the subscription request where there isn't already a transaction email stored" in {
      val res = for {
        _ <- repo.upsertVatNumber(testVatNumber)
        _ <- repo.upsertTransactionEmail(testVatNumber, testEmail)
        model <- repo.findById(testVatNumber)
      } yield model

      await(res) should contain(testSubscriptionRequest)
    }

    "replace an existing stored transaction email" in {
      val newEmail = UUID.randomUUID().toString
      val res = for {
        _ <- repo.insert(testSubscriptionRequest)
        _ <- repo.upsertTransactionEmail(testVatNumber, newEmail)
        model <- repo.findById(testVatNumber)
      } yield model

      await(res) should contain(SubscriptionRequest(testVatNumber, transactionEmail = Some(newEmail)))
    }
  }

  "upsertNino" should {
    val testSubscriptionRequest = SubscriptionRequest(
      vatNumber = testVatNumber,
      nino = Some(testNino),
      ninoSource = Some(UserEntered)
    )

    "throw NoSuchElementException where the vat number doesn't exist" in {
      val res = for {
        _ <- repo.upsertNino(testVatNumber, testNino, UserEntered)
        model <- repo.findById(testVatNumber)
      } yield model

      intercept[NoSuchElementException] {
        await(res)
      }
    }

    "update the subscription request where there isn't already a nino stored" in {
      val res = for {
        _ <- repo.upsertVatNumber(testVatNumber)
        _ <- repo.upsertNino(testVatNumber, testNino, UserEntered)
        model <- repo.findById(testVatNumber)
      } yield model

      await(res) should contain(testSubscriptionRequest)
    }

    "delete the company number if it already exists" in {
      val res = for {
        _ <- repo.upsertVatNumber(testVatNumber)
        _ <- repo.upsertCompanyNumber(testVatNumber, testCompanyNumber)
        withCompanyNumber <- repo.findById(testVatNumber)
        _ <- repo.upsertNino(testVatNumber, testNino, UserEntered)
        withoutCompanyNumber <- repo.findById(testVatNumber)
      } yield (withCompanyNumber, withoutCompanyNumber)

      val (withCompanyNumber, withoutCompanyNumber) = await(res)

      withCompanyNumber should contain(testSubscriptionRequest.copy(companyNumber = Some(testCompanyNumber), nino = None, ninoSource = None))
      withoutCompanyNumber should contain(testSubscriptionRequest)
    }

    "set identity verification to false" in {
      val res = for {
        _ <- repo.upsertVatNumber(testVatNumber)
        _ <- repo.upsertIdentityVerified(testVatNumber)
        identityVerified <- repo.findById(testVatNumber)
        _ <- repo.upsertNino(testVatNumber, testNino, UserEntered)
        identityNotVerified <- repo.findById(testVatNumber)
      } yield (identityVerified, identityNotVerified)

      val (identityVerified, identityNotVerified) = await(res)

      identityVerified should contain(testSubscriptionRequest.copy(identityVerified = true, nino = None, ninoSource = None))
      identityNotVerified should contain(testSubscriptionRequest)
    }


    "replace an existing stored nino" in {
      val newNino = UUID.randomUUID().toString
      val res = for {
        _ <- repo.insert(testSubscriptionRequest)
        _ <- repo.upsertNino(testVatNumber, newNino, UserEntered)
        model <- repo.findById(testVatNumber)
      } yield model

      await(res) should contain(SubscriptionRequest(testVatNumber, nino = Some(newNino), ninoSource = Some(UserEntered)))
    }
  }

  "upsertIdentityVerified" should {
    "throw NoSuchElementException where the vat number doesn't exist" in {
      val res = for {
        _ <- repo.upsertIdentityVerified(testVatNumber)
        model <- repo.findById(testVatNumber)
      } yield model

      intercept[NoSuchElementException] {
        await(res)
      }
    }

    "update the subscription request with IdentityVerified" in {
      val res = for {
        _ <- repo.upsertVatNumber(testVatNumber)
        _ <- repo.upsertIdentityVerified(testVatNumber)
        model <- repo.findById(testVatNumber)
      } yield model

      await(res) should contain(SubscriptionRequest(
        vatNumber = testVatNumber,
        identityVerified = true
      ))
    }
  }

  "upsertCtReference" should {
    "throw NoSuchElementException where the vat number doesn't exist" in {
      val res = for {
        _ <- repo.upsertCtReference(testVatNumber, testCtReference)
        model <- repo.findById(testVatNumber)
      } yield model

      intercept[NoSuchElementException] {
        await(res)
      }
    }

    "update the subscription request with CtReference" in {
      val res = for {
        _ <- repo.upsertVatNumber(testVatNumber)
        _ <- repo.upsertCtReference(testVatNumber, testCtReference)
        model <- repo.findById(testVatNumber)
      } yield model

      await(res) should contain(SubscriptionRequest(
        vatNumber = testVatNumber,
        ctReference = Some(testCtReference)
      ))
    }
  }

  "deleteRecord" should {
    "delete the entry stored against the vrn" in {
      val res = for {
        _ <- repo.upsertVatNumber(testVatNumber)
        inserted <- repo.findById(testVatNumber)
        _ <- repo.deleteRecord(testVatNumber)
        postDelete <- repo.findById(testVatNumber)
      } yield (inserted, postDelete)

      val (inserted, postDelete) = await(res)
      inserted should contain(SubscriptionRequest(testVatNumber))
      postDelete shouldBe None
    }
  }

}
