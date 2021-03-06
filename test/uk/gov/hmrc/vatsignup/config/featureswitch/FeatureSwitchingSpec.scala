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

package uk.gov.hmrc.vatsignup.config.featureswitch

import uk.gov.hmrc.play.test.UnitSpec

class FeatureSwitchingSpec extends UnitSpec with FeatureSwitching {

  val testFeatureSwitch = MTDEligibilityCheck

  "isEnabled" should {
    "return true when a feature switch is set" in {
      enable(testFeatureSwitch)
      isEnabled(testFeatureSwitch) shouldBe true
    }

    "return false when a feature switch is set to false" in {
      disable(testFeatureSwitch)
      isEnabled(testFeatureSwitch) shouldBe false
    }

    "return false when a feature switch has not been set" in {
      sys.props -= testFeatureSwitch.name
      sys.props.get(testFeatureSwitch.name) shouldBe empty
      isEnabled(testFeatureSwitch) shouldBe false
    }
  }

}
