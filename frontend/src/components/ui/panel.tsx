import type { ReactNode } from 'react'
import { Box, Flex, Heading, Text } from '@chakra-ui/react'
import type { BoxProps } from '@chakra-ui/react'

/** The sheet a page is built from: an ink line at the panel radius. */
export function Panel(props: BoxProps) {
  return (
    <Box
      bg="bg.panel"
      borderWidth="ink"
      borderColor="border.control"
      borderRadius="panel"
      px={{ base: '16px', sm: '20px' }}
      py="18px"
      mb="20px"
      {...props}
    />
  )
}

/**
 * A panel's title in the pixel display face, with an optional action on the trailing edge; `id` lets the panel be named by it.
 * `subtitle` is a second line under the title, for the name of the mechanism behind a title that says the outcome.
 */
export function PanelHead(props: {
  title: string
  subtitle?: string
  action?: ReactNode
  id?: string
}) {
  const title = (
    <Heading
      id={props.id}
      as="h2"
      flex="1"
      fontSize="26px"
      lineHeight="1"
      fontWeight="normal"
      textTransform="uppercase"
      letterSpacing="0.01em"
      textWrap="balance"
    >
      {props.title}
    </Heading>
  )
  return (
    <Flex align="center" gap="3" wrap="wrap" mb="14px">
      {props.subtitle === undefined ? (
        title
      ) : (
        <Box flex="1" minW="0">
          {title}
          <Text
            mt="6px"
            fontFamily="mono"
            fontSize="11px"
            fontWeight="semibold"
            lineHeight="16px"
            letterSpacing="0.06em"
            textTransform="uppercase"
            color="fg.muted"
          >
            {props.subtitle}
          </Text>
        </Box>
      )}
      {props.action}
    </Flex>
  )
}
