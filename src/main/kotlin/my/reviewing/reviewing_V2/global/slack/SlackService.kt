package my.reviewing.reviewing_V2.global.slack

import com.slack.api.Slack
import com.slack.api.model.block.Blocks.divider
import com.slack.api.model.block.Blocks.header
import com.slack.api.model.block.Blocks.image
import com.slack.api.model.block.Blocks.section
import com.slack.api.model.block.composition.BlockCompositions.markdownText
import com.slack.api.model.block.composition.BlockCompositions.plainText
import com.slack.api.methods.request.chat.ChatPostMessageRequest
import my.reviewing.reviewing_V2.member.entity.Member
import my.reviewing.reviewing_V2.review.entity.Review
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Service

@Service
class SlackService(
    @Value("\${slack.bot-token}") private val token: String,
    @Value("\${slack.channel.monitor}") private val channel: String,
    @Value("\${slack.channel.member}") private val channelForMember: String
) {

    fun sendMessageToSlack(review: Review) {
        val textObjects = listOf(
            markdownText("*작성자:* ${review.member.name}\n"),
            markdownText("*강의 이름:* ${review.course.title}\n"),
            markdownText("*강의 url:* ${review.course.url}\n"),
            markdownText("*리뷰 평점:* ${review.rating}\n"),
            markdownText("*리뷰 내용:* ${review.content}\n")
        )

        val methods = Slack.getInstance().methods(token)
        val blocks = listOf(
            header { it.text(plainText("새로운 리뷰가 작성되었습니다!")) },
            divider(),
            section { it.fields(textObjects) },
            image { it.imageUrl(review.certification).altText("증명 자료 이미지") }
        )

        val request = ChatPostMessageRequest.builder()
            .channel(channel)
            .blocks(blocks)
            .build()

        try {
            methods.chatPostMessage(request)
        } catch (e: Exception) {
            throw RuntimeException("Failed to send message to Slack", e)
        }
    }

    fun sendMessageToSlackForNewMember(member: Member) {
        val textObjects = listOf(
            markdownText("*이름:* ${member.name}\n")
        )

        val methods = Slack.getInstance().methods(token)
        val blocks = listOf(
            header { it.text(plainText("새로운 회원이 가입했습니다!")) },
            divider(),
            section { it.fields(textObjects) }
        )

        val request = ChatPostMessageRequest.builder()
            .channel(channelForMember)
            .blocks(blocks)
            .build()

        try {
            methods.chatPostMessage(request)
        } catch (e: Exception) {
            throw RuntimeException("Failed to send message to Slack", e)
        }
    }
}
