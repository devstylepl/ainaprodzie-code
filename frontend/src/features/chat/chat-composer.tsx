import { useState, type FormEvent, type KeyboardEvent, type ReactNode, type Ref } from 'react'
import { Button, chakra, Flex, Text } from '@chakra-ui/react'
import { useTranslation } from 'react-i18next'
import { FieldTextarea } from '../../components/ui/form-field.tsx'

// The contract's maxLength of ChatMessageRequest.text, which the server checks after trimming.
const MAX_LENGTH = 2000

/**
 * The question box at the foot of the panel. It stays enabled while an answer
 * streams, so the next question can be typed, but sending waits; Stop takes
 * Send's slot at the same width, so neither the box nor the button moves.
 */
export function ChatComposer({
  streaming,
  onSend,
  onStop,
  inputRef,
  context,
  placeholder,
}: {
  streaming: boolean
  onSend: (question: string) => void
  onStop: () => void
  inputRef: Ref<HTMLTextAreaElement>
  /** What the next question is about, drawn above the box. */
  context: ReactNode
  placeholder: string
}) {
  const { t } = useTranslation('chat')
  const [text, setText] = useState('')

  const onSubmit = (event: FormEvent) => {
    event.preventDefault()
    const question = text.trim()
    if (streaming || question === '') return
    onSend(question)
    setText('')
  }

  // Enter sends and Shift+Enter starts a new line, as in other chats.
  const onKeyDown = (event: KeyboardEvent<HTMLTextAreaElement>) => {
    if (event.key === 'Enter' && !event.shiftKey && !event.nativeEvent.isComposing) {
      event.preventDefault()
      event.currentTarget.form?.requestSubmit()
    }
  }

  return (
    <chakra.form
      onSubmit={onSubmit}
      noValidate
      flexShrink="0"
      bg="bg.subtle"
      borderTopWidth="ink"
      borderColor="border.control"
      px="16px"
      pt="12px"
      pb="10px"
    >
      {context}
      <Flex gap="8px" align="flex-end">
        <FieldTextarea
          ref={inputRef}
          aria-label={t('composer.label')}
          placeholder={placeholder}
          value={text}
          onChange={(event) => setText(event.target.value)}
          onKeyDown={onKeyDown}
          maxLength={MAX_LENGTH}
          rows={2}
          h="64px"
          minH="64px"
          resize="none"
          fontSize="14px"
        />
        {streaming ? (
          <Button type="button" variant="outline" {...SLOT} onClick={onStop}>
            {t('composer.stop')}
          </Button>
        ) : (
          <Button type="submit" colorPalette="accent" {...SLOT}>
            {t('composer.send')}
          </Button>
        )}
      </Flex>
      <Text fontSize="12px" color="fg.muted" mt="8px">
        {t('readOnly')}
      </Text>
    </chakra.form>
  )
}

// The box's height less its 3px shadow, so the button's bottom edge and shadow end with the box.
const SLOT = { w: '96px', h: '44px', mb: '3px', flexShrink: '0' } as const
