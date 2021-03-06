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

package uk.gov.hmrc.vatsignup.httpparsers

import cats.data.NonEmptyList
import play.api.http.Status._
import uk.gov.hmrc.http.{HttpReads, HttpResponse}
import uk.gov.hmrc.vatsignup.models.ControlListInformation
import uk.gov.hmrc.vatsignup.utils.controllist.ControlListInformationParser

object KnownFactsAndControlListInformationHttpParser {
  type KnownFactsAndControlListInformationHttpParserResponse = Either[KnownFactsAndControlListInformationFailure, KnownFactsAndControlListInformation]

  val postcodeKey = "postcode"
  val registrationDateKey = "dateOfReg"
  val controlListInformationKey = "controlListInformation"

  val invalidJsonResponseMessage = "Invalid JSON response"

  implicit object KnownFactsAndControlListInformationHttpReads extends HttpReads[KnownFactsAndControlListInformationHttpParserResponse] {
    override def read(method: String, url: String, response: HttpResponse): KnownFactsAndControlListInformationHttpParserResponse = {
      response.status match {
        case OK =>
          (for {
            businessPostcode <- (response.json \ postcodeKey).validate[String]
            vatRegistrationDate <- (response.json \ registrationDateKey).validate[String]
            controlList <- (response.json \ controlListInformationKey).validate[String]
          } yield ControlListInformationParser.tryParse(controlList) match {
            case Right(validControlList) =>
              Right(KnownFactsAndControlListInformation(businessPostcode, vatRegistrationDate, validControlList))
            case Left(parsingError) =>
              Left(UnexpectedKnownFactsAndControlListInformationFailure(OK, parsingError.toString))
          }) getOrElse Left(UnexpectedKnownFactsAndControlListInformationFailure(OK, invalidJsonResponseMessage))
        case BAD_REQUEST =>
          Left(KnownFactsInvalidVatNumber)
        case NOT_FOUND =>
          Left(ControlListInformationVatNumberNotFound)
        case status =>
          Left(UnexpectedKnownFactsAndControlListInformationFailure(status, response.body))
      }
    }
  }

  case class KnownFactsAndControlListInformation(businessPostcode: String, vatRegistrationDate: String, controlListInformation: ControlListInformation)

  sealed trait KnownFactsAndControlListInformationFailure

  case object KnownFactsInvalidVatNumber extends KnownFactsAndControlListInformationFailure

  case object ControlListInformationVatNumberNotFound extends KnownFactsAndControlListInformationFailure

  case class UnexpectedKnownFactsAndControlListInformationFailure(status: Int, body: String) extends KnownFactsAndControlListInformationFailure

}
