import { useRef, useState } from 'react'
import { flushSync } from 'react-dom'
import { Box, Button, Flex, Link, SkipNavLink, Text, useBreakpointValue } from '@chakra-ui/react'
import { useQueryClient } from '@tanstack/react-query'
import { Outlet, Link as RouterLink, useLocation, useNavigate } from 'react-router'
import { useTranslation } from 'react-i18next'
import {
  useChangeLanguage,
  useLogout,
  useSession,
  type SessionUser,
} from '../../features/auth/use-auth.ts'
import { CHAT_PANEL_ID, ChatPanel } from '../../features/chat/chat-panel.tsx'
import { ServiceFooter } from '../../features/meta/service-footer.tsx'
import { CallerAffiliation } from '../../features/organization/caller-affiliation.tsx'
import { Chip } from './chip.tsx'
import { Lamp } from './lamp.tsx'
import { LanguageSelector } from './language-selector.tsx'

type Role = SessionUser['roles'][number]

const MAIN_ID = 'main-content'

const ROLE_LABEL_KEYS: Record<Role, string> = {
  USER: 'shell.roles.USER',
  SUPPORT: 'shell.roles.SUPPORT',
}

/**
 * The signed-in frame: app name, the chat toggle, language switch, who is
 * signed in and the way out, over a centred content column and the chat panel
 * (chat ADR 0006). The page scrolls inside the main column, so the panel keeps
 * its height beside it. Mounted inside `SessionGate`, so the session is always
 * present by the time it renders.
 */
export function AppShell() {
  const { t } = useTranslation('common')
  const { t: tChat } = useTranslation('chat')
  const [chatOpen, setChatOpen] = useState(false)
  const chatToggle = useRef<HTMLButtonElement>(null)
  const mainRef = useRef<HTMLElement>(null)
  // Below `lg` the panel covers the page: the page is inert behind it, and
  // following a link closes it to show the ticket.
  const chatCoversPage = useBreakpointValue({ base: true, lg: false }) ?? true
  const navigate = useNavigate()
  const onSettings = useLocation().pathname === '/settings'
  const queryClient = useQueryClient()
  const user = useSession().data
  const logout = useLogout()
  const changeLanguage = useChangeLanguage()

  if (!user) return null

  // Navigate before clearing: a cleared cache under the still-mounted gate
  // would refetch the session and race the redirect.
  const onLogout = () =>
    logout.mutate(undefined, {
      onSuccess: () => {
        void navigate('/login', { replace: true })
        queryClient.clear()
      },
    })

  const closeChat = () => {
    setChatOpen(false)
    chatToggle.current?.focus()
  }

  // The page stops being inert only once the panel's close is rendered, and
  // focus can only move to it then.
  const followChatLink = () => {
    if (!chatCoversPage) return
    flushSync(() => setChatOpen(false))
    mainRef.current?.focus({ preventScroll: true })
  }

  return (
    <Flex direction="column" h="100dvh" bg="bg">
      {/* The first Tab stop: past the header to the page, without a change of URL. */}
      <SkipNavLink
        id={MAIN_ID}
        onClick={(event) => {
          event.preventDefault()
          document.getElementById(MAIN_ID)?.focus()
        }}
        zIndex="skipNav"
        color="fg"
        fontSize="14px"
        fontWeight="semibold"
        borderWidth="ink"
        borderColor="border.control"
        borderRadius="control"
        px="16px"
        boxShadow="raised"
        _focusVisible={{
          outline: '2px solid',
          outlineColor: 'accent.focusRing',
          outlineOffset: '2px',
        }}
      >
        {t('shell.skipToContent')}
      </SkipNavLink>
      <Flex
        as="header"
        flexShrink="0"
        align="center"
        wrap="wrap"
        columnGap={{ base: '8px', sm: '16px' }}
        rowGap="8px"
        bg="bg"
        borderBottomWidth="ink"
        borderColor="border.control"
        px={{ base: '16px', sm: '24px' }}
        py="10px"
      >
        <Text
          fontFamily="heading"
          fontSize={{ base: '26px', sm: '30px' }}
          lineHeight="1"
          textTransform="uppercase"
          letterSpacing="0.01em"
          // The name never gives way: where the bar runs out of room its controls wrap under it.
          flex="1"
          whiteSpace="nowrap"
        >
          <Link
            asChild
            color="fg"
            gap="10px"
            borderRadius="control"
            _hover={{ textDecoration: 'none' }}
            _focusVisible={{ outline: '2px solid', outlineColor: 'accent.focusRing' }}
          >
            <RouterLink to="/">
              {/* The course's mark: three lamps, one of each signal. On a phone the name
                  alone, as the chat toggle leaves the bar no room for both. */}
              <Flex as="span" gap="3px" aria-hidden="true" display={{ base: 'none', sm: 'flex' }}>
                <Lamp tone="red" size="8px" />
                <Lamp tone="amber" size="8px" />
                <Lamp tone="green" size="8px" />
              </Flex>
              {t('app.name')}
            </RouterLink>
          </Link>
        </Text>
        {user.roles.includes('SUPPORT') && (
          <Button
            asChild
            variant="outline"
            size={{ base: 'xs', sm: 'sm' }}
            flexShrink="0"
            // Open, it stays pressed into its shadow like the chat toggle.
            _currentPage={{ bg: 'bg.muted', transform: 'translate(3px, 3px)', boxShadow: 'none' }}
          >
            <RouterLink to="/settings" aria-current={onSettings ? 'page' : undefined}>
              {tChat('settings.link')}
            </RouterLink>
          </Button>
        )}
        <Button
          ref={chatToggle}
          type="button"
          variant="outline"
          // A size smaller on a phone, so the wordmark keeps its room beside the chat toggle.
          size={{ base: 'xs', sm: 'sm' }}
          flexShrink="0"
          // Open, it stays pressed into its shadow, a switch latched on.
          _expanded={{ bg: 'bg.muted', transform: 'translate(3px, 3px)', boxShadow: 'none' }}
          aria-expanded={chatOpen}
          aria-controls={CHAT_PANEL_ID}
          onClick={() => setChatOpen((open) => !open)}
        >
          {tChat('toggle')}
        </Button>
        <Box flexShrink="0">
          <LanguageSelector onLanguageChange={(language) => changeLanguage.mutate(language)} />
        </Box>
        {/* On a phone who is signed in takes its own line under the bar, without the email. */}
        <Flex
          order={{ base: 1, sm: 0 }}
          flexBasis={{ base: '100%', sm: 'auto' }}
          direction={{ base: 'row', sm: 'column' }}
          align={{ base: 'center', sm: 'flex-end' }}
          gap={{ base: '8px', sm: '0' }}
          textAlign={{ base: 'left', sm: 'right' }}
          fontSize="14px"
          minW="0"
        >
          <Box minW="0" truncate>
            {user.displayName}
            {user.roles.map((role) => (
              <Box as="span" key={role} ms="8px">
                <Chip tone="ink">
                  {/* A role newer than this bundle shows its raw code, not a wrong label. */}
                  {ROLE_LABEL_KEYS[role] ? t(ROLE_LABEL_KEYS[role]) : role}
                </Chip>
              </Box>
            ))}
          </Box>
          <Text as="small" display={{ base: 'none', sm: 'block' }} color="fg.muted" fontSize="12px">
            {user.email}
          </Text>
          <CallerAffiliation id={user.id} roles={user.roles} />
        </Flex>
        <Button
          type="button"
          variant="outline"
          size={{ base: 'xs', sm: 'sm' }}
          disabled={logout.isPending}
          onClick={onLogout}
        >
          {t('shell.logout')}
        </Button>
      </Flex>
      <Flex flex="1" minH="0" position="relative">
        <ChatPanel open={chatOpen} onClose={closeChat} onFollowLink={followChatLink} />
        {/* Positioned, so absolutely placed content (a visually hidden header) scrolls with it rather than stretching the page. */}
        <Box
          as="main"
          id={MAIN_ID}
          ref={mainRef}
          tabIndex={-1}
          inert={chatOpen && chatCoversPage}
          flex="1"
          minW="0"
          overflowY="auto"
          position="relative"
          // Focus lands here after a chat link opens a page; the region itself is not a control.
          _focus={{ outline: 'none' }}
        >
          <Box
            maxW="1200px"
            mx="auto"
            px={{ base: '16px', sm: '24px' }}
            py={{ base: '20px', sm: '28px' }}
          >
            <Outlet />
            <ServiceFooter />
          </Box>
        </Box>
      </Flex>
    </Flex>
  )
}
