package com.gitub.oopgurus.refactoringproblems.mailserver

import mu.KotlinLogging
import org.springframework.mail.javamail.JavaMailSender
import org.springframework.stereotype.Component
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit


@Component
class MailService(
    private val mailTemplateRepository: MailTemplateRepository,
    private val postOfficeBuilderFactory: PostOfficeBuilderFactory,
) {
    fun send(sendMailDtos: List<SendMailDto>) {
        sendMailDtos.forEach {
            sendSingle(it)
        }
    }

    private fun sendSingle(sendMailDto: SendMailDto) {
        val postOffice = postOfficeBuilderFactory.create()
            .toAddress(sendMailDto.toAddress)
            .fromName(sendMailDto.fromName)
            .fromAddress(sendMailDto.fromAddress)
            .title(sendMailDto.title)
            .htmlTemplateName(sendMailDto.htmlTemplateName)
            .htmlTemplateParameters(sendMailDto.htmlTemplateParameters)
            .fileAttachments(sendMailDto.fileAttachments)
            .sendAfterSeconds(sendMailDto.sendAfterSeconds)
            .build()

        val mailMessage = postOffice.newMailMessage()

        // 여기 아래부터는 "동작"을 나타냄
        // 어떤 동작들이 있는지 구분해보자면? (비개발자에게 설명한다고 하면 어떻게 말하겠는가?)
        // 1. 메일을 보낸다.
        //   - 바로 보낸다
        //   - 몇 초 뒤에 보낸다
        // 2. 그 결과를 저장한다
        //   - 발송이 성공한 경우
        //   - 발송이 실패한 경우 (실패한 이유도 같이)
        //   - db 에 / 로그에 각각
        //
        //
        // 여기서 동작과 그 동작의 세부사항을 구분할 수 있겠는가?
        // 동작1: 메일을 보낸다
        // 세부사항: 바로 보낸다 / 몇 초 뒤에 보낸다
        // 동작2: 그 결과를 저장한다
        // 세부사항: 발송이 성공한 경우 / 발송이 실패한 경우 (실패한 이유도 같이)
        // 세부사항: db에 저장 / 로그에 저장

        // 코드에서는 무엇을 어떻게 표현해야 하지?
        // 무엇을: 동작을
        // 어떻게: 세부사항을 숨겨서


        // 동작2(결과를 저장한다)는 동작1(메일을 보낸다)에 의존적이므로 다음과 같이 표현
        mailMessage.send().register()
    }

    fun creatMailTemplate(createMailTemplateDtos: List<CreateMailTemplateDto>) {
        createMailTemplateDtos.forEach {
            if (it.htmlBody.isBlank()) {
                throw IllegalArgumentException("htmlBody is blank")
            }
            mailTemplateRepository.save(
                MailTemplateEntity(
                    name = it.name,
                    htmlBody = it.htmlBody,
                )
            )
        }
    }
}

