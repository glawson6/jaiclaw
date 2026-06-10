# What Is JaiClaw? A Plain-Language Guide

## What Is an "AI Agent" and Why Should I Care?

You already know what ChatGPT is — you type a question, it types an answer. That's a chatbot. Useful, but limited. It can't check your database, send an email, look up a customer record, or file a report. It just talks.

An **AI agent** is what happens when you give that same intelligence the ability to *do things*. Instead of just answering questions, an agent can:

- Look up information in your systems
- Send messages to people
- Fill out forms and create reports
- Monitor data and alert you when something changes
- Follow multi-step processes the way an employee would

Think of the difference between asking someone for directions (chatbot) versus hiring someone who drives you there, stops for gas, and texts your friend that you're on the way (agent).

An **agentic framework** is the platform that makes building these agents possible without starting from scratch. It handles all the plumbing — connecting to messaging apps, managing conversations, keeping things secure, remembering past interactions — so you can focus on what you actually want the agent to do.

---

## What Is JaiClaw?

JaiClaw is an agentic framework. It is the platform you use to build, deploy, and run AI agents that solve real problems for your organization.

Here is what that means in practical terms:

**You tell JaiClaw what your agent should do.** Maybe it answers customer questions. Maybe it monitors equipment and alerts your team when something breaks. Maybe it processes intake forms. You define the job.

**JaiClaw connects the agent to the tools it needs.** Your databases, your APIs, your file systems, web searches — whatever the agent needs to get its job done.

**JaiClaw connects the agent to the people it serves.** Your staff and customers can interact with the agent through the communication tools they already use — Slack, Microsoft Teams, Telegram, Discord, email, text messages, or a web interface.

**JaiClaw handles everything else.** Security. Conversation history. User identity. Audit trails. Scheduling. Monitoring. All the infrastructure that would take a development team months to build from scratch.

### One Agent, Every Channel

One of JaiClaw's most powerful capabilities is that a single agent can be available across multiple communication channels simultaneously. The same agent that answers questions in Slack also responds to text messages and emails — and it remembers the conversation regardless of which channel someone uses.

```
                    ┌─── Slack
                    ├─── Microsoft Teams
                    ├─── Telegram
  Your AI Agent ────├─── Discord
                    ├─── Email
                    ├─── Text Message (SMS)
                    ├─── Signal
                    └─── Web Interface
```

A customer who emails a question on Monday and texts a follow-up on Wednesday gets a continuous conversation — not a cold start.

---

## Why Should Your Organization Care?

### The Short Answer

Every organization has processes that involve a person gathering information, making routine decisions, communicating results, and repeating. AI agents can handle these processes — faster, around the clock, and without the errors that come from fatigue and repetition.

JaiClaw is the platform that turns that possibility into reality without requiring your team to become AI researchers.

### The Longer Answer, by Organization Type

#### Businesses and Corporations

**The problem you have:** Your team spends hours on tasks that follow predictable patterns — answering the same customer questions, generating the same reports, monitoring the same dashboards, processing the same forms. These tasks are too complex for simple automation but too routine for your best people.

**What JaiClaw gives you:**
- Customer support agents that handle common inquiries across every channel your customers use, escalating to humans only when needed
- Internal assistants that answer employee questions about policies, benefits, and procedures — drawing from your actual documentation, not generic training data
- Monitoring agents that watch your systems, data, and processes and alert the right people when something needs attention
- Report generators that pull data from your systems, analyze it, and deliver formatted reports on a schedule

**Real example:** A logistics company deploys a JaiClaw agent across SMS and Slack. Customers text to ask "where's my shipment?" The agent checks the carrier API, gets the current status, calculates the estimated arrival, and responds — in seconds, 24/7, in the channel the customer already uses. When an issue requires human intervention, the agent creates a ticket with full context and routes it to the right team.

#### Schools and Universities

**The problem you have:** Administrative staff are overwhelmed by repetitive inquiries from students, parents, and faculty. Students need academic support outside office hours. Institutional data is scattered across systems that don't talk to each other.

**What JaiClaw gives you:**
- Enrollment assistants that guide prospective students through applications, answer questions about programs and requirements, and follow up automatically
- Academic advisors that help students find courses, check prerequisites, and plan their schedules — available at midnight before registration opens
- IT helpdesk agents that walk students and staff through common tech issues (Wi-Fi, LMS access, password resets) before creating a ticket
- Research assistants that help faculty search literature, summarize papers, and organize citations
- Campus safety agents that students can text to report concerns, get emergency information, or request escorts

**Real example:** A university deploys a JaiClaw agent on Telegram and SMS. Students text to ask about financial aid deadlines, registration holds, or campus events. The agent pulls answers from the university's actual systems and policies — not generic information. During advising season, it handles 80% of routine questions, freeing advisors for the conversations that require human judgment.

#### Government Agencies

**The problem you have:** Residents need services but can't navigate your systems. Staff are buried in paperwork. Compliance reporting consumes days that could be spent serving the public. Public information requests pile up.

**What JaiClaw gives you:**
- Citizen service agents that answer questions about permits, licenses, utilities, and services across SMS, email, and web — in plain language, 24/7
- Compliance monitoring agents that check regulatory requirements on a schedule and generate reports automatically
- Public records request processors that search archives, identify responsive documents, and draft response packages
- Internal workflow agents that route requests, track approvals, and send reminders so nothing falls through the cracks

**Real example:** A county government deploys a JaiClaw agent on SMS and their website. Residents text to ask about property tax due dates, trash pickup schedules, or permit requirements. The agent queries the county's GIS system and databases to give specific answers for the resident's address — not generic information. Staff time spent answering phones drops by 60%.

#### Nonprofits and Community Organizations

**The problem you have:** Small staff, big mission. You need to stretch every dollar, coordinate volunteers, track program impact, communicate with stakeholders, and write grant reports — often simultaneously.

**What JaiClaw gives you:**
- Volunteer coordinators that manage schedules, send reminders, and fill last-minute gaps via text message
- Donor engagement agents that send personalized impact updates and respond to donor inquiries
- Program intake agents that guide clients through eligibility screening and enrollment via the channels they actually use (often SMS)
- Grant reporting assistants that pull program data and draft narrative reports aligned with funder requirements

**Real example:** A food bank deploys a JaiClaw agent on SMS. Clients text to find the nearest distribution site, check hours, and learn about eligibility for additional programs. Volunteers receive automated shift reminders and can confirm or swap via text. The executive director asks the agent for program statistics and gets a formatted summary pulled from their actual data — no spreadsheet digging required.

#### Healthcare Organizations

**The problem you have:** Patients need information between appointments. Staff spend hours on phone calls that follow scripts. Care coordination across providers is fragmented. Compliance documentation is relentless.

**What JaiClaw gives you:**
- Patient communication agents that send appointment reminders, answer pre-visit questions, and provide post-visit instructions via the patient's preferred channel
- Triage assistants that help patients determine the appropriate level of care based on their symptoms and direct them accordingly
- Care coordination agents that track referrals, follow up on outstanding orders, and alert staff when patients fall through the cracks
- Compliance documentation agents that generate required reports from clinical system data on a schedule

**Real example:** A community health center deploys a JaiClaw agent on SMS. Patients receive appointment reminders and can confirm, reschedule, or ask questions by text — in English or Spanish. No-show rates drop 25%. After visits, the agent sends care instructions and medication reminders. The agent handles 200+ daily interactions that previously required staff phone time.

---

## How JaiClaw Works — Without the Technical Jargon

### The Building Blocks

Think of JaiClaw as a set of building blocks that snap together. You pick the blocks you need:

| Block | What It Does | Plain-Language Example |
|-------|-------------|----------------------|
| **Agent Runtime** | The brain — sends questions to the AI, interprets answers, decides what to do next | "The part that thinks" |
| **Tools** | Actions the agent can take — look things up, send messages, create records | "The part that does things" |
| **Channels** | How people talk to the agent — Slack, Teams, text, email, web | "The part that listens and responds" |
| **Memory** | Remembers past conversations and learned information | "The part that doesn't forget" |
| **Skills** | Instructions that tell the agent how to behave in specific situations | "The part that follows your playbook" |
| **Plugins** | Custom extensions your team builds for your specific needs | "The part you customize" |
| **Security** | Controls who can access what, verifies identities | "The part that keeps things safe" |
| **Audit** | Records everything the agent does for compliance and review | "The part that keeps receipts" |
| **Scheduling** | Runs tasks on a schedule without anyone asking | "The part that works while you sleep" |

### A Day in the Life of a JaiClaw Agent

Here is what happens when someone sends a message to a JaiClaw-powered agent:

```
  1. Maria texts "What's my account balance?" to the company SMS number

  2. JaiClaw receives the text through the SMS channel adapter

  3. JaiClaw identifies Maria from her phone number (identity)

  4. JaiClaw loads Maria's conversation history (memory)

  5. The agent understands the question and decides it needs
     to check the billing system (reasoning)

  6. The agent calls the "check_balance" tool, which queries
     your billing database (tool execution)

  7. The agent gets the result: $247.50 due on April 15

  8. The agent composes a friendly response and sends it
     back via SMS (channel delivery)

  9. The interaction is logged (audit)

  10. Maria receives: "Hi Maria! Your current balance is
      $247.50, due April 15. Want me to send a payment link?"
```

Total time: 3 seconds. No human staff involved. Available at 2 AM on a Sunday.

---

## What Makes JaiClaw Different

### Built for the Real World

Many AI tools are impressive demos that fall apart in production. JaiClaw is designed for the unglamorous realities of real organizations:

- **Security matters.** JaiClaw has built-in authentication, user isolation, and access controls. Your agent doesn't accidentally show Customer A's data to Customer B.

- **Compliance matters.** Every action the agent takes is logged with timestamps, user identity, and full context. When an auditor asks "what did the system do and why?" you have the answer.

- **Reliability matters.** JaiClaw runs on Java and Spring Boot — the same technology that powers banking systems, healthcare platforms, and government services. Not a research prototype.

- **Scale matters.** One JaiClaw deployment handles hundreds of concurrent conversations across all channels. You don't need a separate system for each communication platform.

### You Keep Control

JaiClaw agents do what you tell them to do — nothing more. You define:

- **What tools the agent can use** — it can only access the systems you connect
- **What actions require human approval** — critical operations can require a person to confirm before the agent proceeds
- **Who can interact with the agent** — access controls determine which users and which channels are active
- **How the agent behaves** — skills and system prompts set the tone, boundaries, and domain expertise

The agent cannot go rogue because it has no capabilities beyond what you explicitly provide.

### Open and Extensible

JaiClaw is open source. You can inspect every line of code. You own your deployment. Your data stays on your infrastructure. There is no vendor lock-in, no per-message pricing from a platform provider, and no dependency on a third-party service that might change terms or shut down.

You bring your own AI provider — Anthropic (Claude), OpenAI (GPT), Google (Gemini), or a local model via Ollama. JaiClaw works with all of them.

---

## Common Questions

**"Do we need AI engineers to use this?"**
You need Java developers — the same people already building your applications. JaiClaw is built on Spring Boot, the most widely used Java framework in the world. If your team can build a Spring Boot web application, they can build a JaiClaw agent.

**"How much does it cost?"**
JaiClaw itself is free and open source. Your costs are:
- AI provider API usage (you choose the provider and model — costs range from fractions of a penny to a few cents per interaction depending on the model)
- Your existing server infrastructure (JaiClaw runs anywhere Java runs)
- Your team's development time to configure and customize the agent

**"Is our data safe?"**
JaiClaw runs on your infrastructure. Conversations, user data, and business information never leave your systems except when the agent calls the AI provider for reasoning — and you control exactly what context is sent. For maximum data security, you can run a local AI model via Ollama, keeping everything on-premises.

**"How long does it take to get something running?"**
A basic agent with one or two channels can be running in a day. A production deployment with custom tools, security, and multiple channels typically takes 2-4 weeks depending on the complexity of your integrations.

**"Can it replace our employees?"**
JaiClaw agents are best understood as force multipliers, not replacements. They handle the repetitive, high-volume interactions so your people can focus on the work that requires human judgment, empathy, and creativity. The customer service agent handles "what's my balance?" so your support team can handle "I'm in a difficult situation and need help figuring out my options."

**"What if the AI gives a wrong answer?"**
Several safeguards exist:
- The agent only has access to the tools and data you provide — it can't make things up about your systems because it queries them directly
- You can require human approval for consequential actions (refunds, account changes, escalations)
- Audit logging records every interaction for review
- Skills and system prompts constrain the agent's behavior to your defined scope
- You choose the AI model, balancing capability with cost and accuracy for your use case

---

## Getting Started

If this document has sparked ideas about what an AI agent could do for your organization, here are practical next steps:

1. **Identify a pain point.** What process in your organization involves people doing repetitive information lookup, communication, or decision-making? That's your first agent candidate.

2. **Start small.** Don't try to automate everything at once. Pick one specific workflow and one communication channel. Prove the value, then expand.

3. **Talk to your development team.** If you have Java/Spring Boot developers, they already have the skills to build with JaiClaw. If you don't, JaiClaw's modular design means a consultant can get you running quickly.

4. **Explore the examples.** JaiClaw ships with 10 example applications — from a helpdesk bot to a code review assistant to a research agent — that demonstrate real patterns you can adapt.

For technical teams ready to dive in, the [Architecture Guide](ARCHITECTURE.md) and [Operations Guide](OPERATIONS.md) provide the full picture.
