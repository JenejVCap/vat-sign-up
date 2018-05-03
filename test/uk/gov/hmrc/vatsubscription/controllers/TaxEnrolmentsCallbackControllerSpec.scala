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

package uk.gov.hmrc.vatsubscription.controllers

import org.scalatest.mockito.MockitoSugar
import play.api.http.Status._
import play.api.libs.json.{JsValue, Json}
import play.api.mvc.Result
import play.api.test.FakeRequest
import uk.gov.hmrc.play.test.UnitSpec
import uk.gov.hmrc.vatsubscription.helpers.TestConstants.testVatNumber

import scala.concurrent.Future

class TaxEnrolmentsCallbackControllerSpec extends UnitSpec with MockitoSugar {

  object TestTaxEnrolmentsCallbackController
    extends TaxEnrolmentsCallbackController()

  val testRequest: FakeRequest[JsValue] = FakeRequest().withBody(Json.obj(TestTaxEnrolmentsCallbackController.stateKey -> "nnn"))

  "taxEnrolmentsCallback" should {
    "return NO_CONTENT when successful" in {
      val res: Future[Result] = TestTaxEnrolmentsCallbackController.taxEnrolmentsCallback(testVatNumber)(testRequest)
      status(res) shouldBe NO_CONTENT
    }
  }

}
