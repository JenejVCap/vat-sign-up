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

package uk.gov.hmrc.vatsignup.models

import SignUpRequest._

case class SignUpRequest(vatNumber: String,
                         businessEntity: BusinessEntity,
                         signUpEmail: Option[EmailAddress],
                         transactionEmail: EmailAddress,
                         isDelegated: Boolean
                        )

object SignUpRequest {

  sealed trait BusinessEntity

  case class LimitedCompany(companyNumber: String) extends BusinessEntity

  case class SoleTrader(nino: String) extends BusinessEntity

  case class EmailAddress(emailAddress: String, isVerified: Boolean)

}


