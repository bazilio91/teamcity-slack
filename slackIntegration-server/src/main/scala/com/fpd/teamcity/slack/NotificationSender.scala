package com.fpd.teamcity.slack

import com.fpd.teamcity.slack.ConfigManager.BuildSetting
import com.fpd.teamcity.slack.ConfigManager.BuildSettingFlag.BuildSettingFlag
import com.fpd.teamcity.slack.SlackGateway.{SlackChannel, SlackUser}
import jetbrains.buildServer.serverSide.SBuild

trait NotificationSender {

  val configManager: ConfigManager
  val gateway: SlackGateway
  val messageBuilderFactory: MessageBuilderFactory

  import Helpers._

  def send(build: SBuild, flags: Set[BuildSettingFlag]): Unit = {
    val settings = prepareSettings(build, flags)

    lazy val emails = build.committees
    lazy val messageBuilder = messageBuilderFactory.createForBuild(build)
    val sendPersonal = build.getBuildStatus.isFailed

    settings.foreach { setting ⇒
      val attachment = messageBuilder.compile(setting.messageTemplate)
      gateway.sendMessage(SlackChannel(setting.slackChannel), attachment)

      // if build failed all committees should receive the message
      if (sendPersonal) {
        emails.foreach { email ⇒
          gateway.sendMessage(SlackUser(email), attachment)
        }
      }
    }
  }

  def prepareSettings(build: SBuild, flags: Set[BuildSettingFlag]): Iterable[BuildSetting] = {
    def matchBranch(mask: String) = Option(build.getBranch).exists { branch ⇒
      mask.r.findFirstIn(branch.getDisplayName).isDefined
    }

    configManager.buildSettingList(build.getBuildTypeId).values.filter { x ⇒
      x.flags.intersect(flags).nonEmpty && matchBranch(x.branchMask)
    }
  }
}