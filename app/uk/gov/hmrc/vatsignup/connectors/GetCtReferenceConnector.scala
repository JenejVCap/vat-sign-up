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

package uk.gov.hmrc.vatsignup.connectors

import javax.inject.Inject
import uk.gov.hmrc.http.logging.Authorization
import uk.gov.hmrc.http.{HeaderCarrier, HttpReads}
import uk.gov.hmrc.play.bootstrap.http.HttpClient
import uk.gov.hmrc.vatsignup.config.AppConfig
import uk.gov.hmrc.vatsignup.httpparsers.GetCtReferenceHttpParser._

import scala.concurrent.{ExecutionContext, Future}

@Inject
class GetCtReferenceConnector @Inject()(http: HttpClient,
                                        appConfig: AppConfig
                                       )(implicit ec: ExecutionContext) {
  def getCtReference(companyNumber: String)(implicit hc: HeaderCarrier): Future[GetCtReferenceResponse] = {
    val headerCarrier = hc
      .withExtraHeaders(appConfig.desEnvironmentHeader)
      .copy(authorization = Some(Authorization(appConfig.desAuthorisationToken)))

    http.GET(appConfig.getCtReferenceUrl(companyNumber))(
      implicitly[HttpReads[GetCtReferenceResponse]],
      headerCarrier,
      implicitly[ExecutionContext]
    )
  }
}
