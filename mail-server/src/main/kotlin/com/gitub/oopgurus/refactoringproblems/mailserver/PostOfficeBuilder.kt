package com.gitub.oopgurus.refactoringproblems.mailserver

import com.fasterxml.jackson.databind.ObjectMapper
import com.github.jknack.handlebars.Handlebars
import com.github.jknack.handlebars.Template
import org.springframework.http.HttpMethod
import org.springframework.http.client.ClientHttpResponse
import org.springframework.mail.javamail.JavaMailSender
import org.springframework.util.StreamUtils
import org.springframework.util.unit.DataSize
import org.springframework.web.client.RestTemplate
import java.io.File
import java.io.FileOutputStream
import java.util.concurrent.ScheduledExecutorService
import java.util.concurrent.TimeUnit

class PostOfficeBuilder(
    private val mailSpamService: MailSpamService,
    private val mailTemplateRepository: MailTemplateRepository,
    private val handlebars: Handlebars,
    private val objectMapper: ObjectMapper,
    private val restTemplate: RestTemplate,
    private val javaMailSender: JavaMailSender,
    private val mailRepository: MailRepository,
    private val scheduledExecutorService: ScheduledExecutorService,
) {
    private var getToAddress: () -> String = { throw IllegalStateException("getToAddress is not set") }
    private var getFromAddress: () -> String = { throw IllegalStateException("getFromAddress is not set") }
    private var getHtmlTemplateName: () -> String = { throw IllegalStateException("getHtmlTemplateName is not set") }
    private var getHtmlTemplate: () -> Template = { throw IllegalStateException("getHtmlTemplate is not set") }
    private var getTitle: () -> String = { throw IllegalStateException("getTitle is not set") }
    private var getFromName: () -> String = { throw IllegalStateException("getFromName is not set") }
    private var getHtmlTemplateParameters: () -> HtmlTemplateParameters =
        { throw IllegalStateException("getHtmlTemplateParameters is not set") }

    private var getFileAttachmentDtoList: () -> List<FileAttachmentDto> =
        { throw IllegalStateException("getFileAttachmentDtoList is not set") }
    private var getSendAfter: () -> SendAfter? = { throw IllegalStateException("getSendAfter is not set") }


    fun toAddress(toAddress: String): PostOfficeBuilder {
        getToAddress = when {
            mailSpamService.needBlockByDomainName(toAddress) -> {
                { throw RuntimeException("도메인 차단") }
            }

            mailSpamService.needBlockByRecentSuccess(toAddress) -> {
                { throw RuntimeException("최근 메일 발송 실패로 인한 차단") }
            }

            Regex(".+@.*\\..+").matches(toAddress).not() -> {
                { throw RuntimeException("이메일 형식 오류") }
            }

            else -> {
                { toAddress }
            }
        }
        return this
    }

    fun fromAddress(fromAddress: String): PostOfficeBuilder {
        getFromAddress = when {
            Regex(".+@.*\\..+").matches(fromAddress).not() -> {
                { throw RuntimeException("이메일 형식 오류") }
            }

            else -> {
                { fromAddress }
            }
        }
        return this
    }

    fun htmlTemplateName(htmlTemplateName: String): PostOfficeBuilder {
        getHtmlTemplateName = when {
            htmlTemplateName.isBlank() -> {
                { throw RuntimeException("템플릿 이름이 비어있습니다") }
            }

            else -> {
                { htmlTemplateName }
            }
        }

        val htmlTemplate = mailTemplateRepository.findByName(htmlTemplateName)
        getHtmlTemplate = when {
            htmlTemplate == null -> {
                { throw RuntimeException("템플릿이 존재하지 않습니다: [$htmlTemplateName]") }
            }

            else -> {
                val template = handlebars.compileInline(htmlTemplate.htmlBody)
                ({ template })
            }
        }
        return this
    }


    fun title(title: String): PostOfficeBuilder {
        getTitle = when {
            title.isBlank() -> {
                { throw RuntimeException("제목이 비어있습니다") }
            }

            else -> {
                { title }
            }
        }
        return this
    }


    fun fromName(fromName: String): PostOfficeBuilder {
        getFromName = when {
            fromName.isBlank() -> {
                { throw RuntimeException("이름이 비어있습니다") }
            }

            else -> {
                { fromName }
            }
        }
        return this
    }

    fun htmlTemplateParameters(htmlTemplateParameters: Map<String, Any>): PostOfficeBuilder {
        getHtmlTemplateParameters = {
            HtmlTemplateParameters(
                holder = htmlTemplateParameters,
                objectMapper = objectMapper,
            )
        }
        return this
    }

    fun fileAttachments(fileAttachments: List<FileAttachment>): PostOfficeBuilder {
        val fileAttachmentDtoList = fileAttachments.mapIndexed { index, attachment ->
            val fileAttachmentDto = restTemplate.execute(
                attachment.url,
                HttpMethod.GET,
                null,
                { clientHttpResponse: ClientHttpResponse ->
                    val id = "file-${index}-${java.util.UUID.randomUUID()}"
                    val tempFile = File.createTempFile(id, "")
                    StreamUtils.copy(clientHttpResponse.body, FileOutputStream(tempFile))

                    FileAttachmentDto(
                        resultFile = tempFile,
                        name = attachment.name,
                        clientHttpResponse = clientHttpResponse
                    )
                })

            if (fileAttachmentDto == null) {
                throw RuntimeException("파일 초기화 실패")
            }
            if (fileAttachmentDto.resultFile.length() != fileAttachmentDto.clientHttpResponse.headers.contentLength) {
                throw RuntimeException("파일 크기 불일치")
            }
            if (DataSize.ofKilobytes(2048) <= DataSize.ofBytes(fileAttachmentDto.clientHttpResponse.headers.contentLength)) {
                throw RuntimeException("파일 크기 초과")
            }
            fileAttachmentDto
        }

        getFileAttachmentDtoList = { fileAttachmentDtoList }
        return this
    }

    fun sendAfterSeconds(sendAfterSeconds: Long?): PostOfficeBuilder {
        if (sendAfterSeconds == null) {
            getSendAfter = { null }
            return this
        }

        if (sendAfterSeconds <= 0) {
            throw RuntimeException("sendAfterSeconds는 0 이상이어야 합니다")
        }
        getSendAfter = {
            SendAfter(
                amount = sendAfterSeconds,
                unit = TimeUnit.SECONDS,
            )
        }
        return this
    }

    fun build(): PostOffice {
        return PostOffice(
            javaMailSender = javaMailSender,
            getTitle = getTitle,
            getHtmlTemplate = getHtmlTemplate,
            getHtmlTemplateName = getHtmlTemplateName,
            getHtmlTemplateParameters = getHtmlTemplateParameters,
            getFromAddress = getFromAddress,
            getFromName = getFromName,
            getToAddress = getToAddress,
            getFileAttachmentDtoList = getFileAttachmentDtoList,
            getSendAfter = getSendAfter,
            mailRepository = mailRepository,
            scheduledExecutorService = scheduledExecutorService,
        )
    }
}
