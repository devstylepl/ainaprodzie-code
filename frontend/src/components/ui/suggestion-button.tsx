import { Button, type ButtonProps } from '@chakra-ui/react'

/**
 * A question or query the reader can send with one tap. It is a sentence, so it
 * reads in the body face at 14px rather than the recipe's mono capitals, wraps
 * from the leading edge in a control-radius box, and keeps the outline button's
 * ink edge, hard shadow and press.
 */
export function SuggestionButton(props: ButtonProps) {
  return (
    <Button
      type="button"
      variant="outline"
      w="full"
      h="auto"
      minH="44px"
      px="14px"
      py="10px"
      borderRadius="control"
      fontFamily="body"
      fontSize="14px"
      fontWeight="normal"
      lineHeight="1.4"
      letterSpacing="0"
      textTransform="none"
      textAlign="start"
      justifyContent="flex-start"
      whiteSpace="normal"
      {...props}
    />
  )
}
