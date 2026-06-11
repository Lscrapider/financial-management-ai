import { requestClient } from '#/api/request';

export interface InvestorPsychProfileAnswerPayload {
  optionCode: string;
  questionCode: string;
}

export interface InvestorPsychProfileSubmitPayload {
  answers: InvestorPsychProfileAnswerPayload[];
}

export interface InvestorPsychProfileQuestionOption {
  code: string;
  label: string;
}

export interface InvestorPsychProfileQuestion {
  code: string;
  options: InvestorPsychProfileQuestionOption[];
  title: string;
}

export interface InvestorPsychProfileQuestionnaire {
  questions: InvestorPsychProfileQuestion[];
}

export interface InvestorPsychProfile {
  adviceStyle?: null | string;
  decisionStyle?: null | string;
  explanationPreference?: null | string;
  holdingMindset: string[];
  profileCompleted: boolean;
  profileVersion?: null | number;
  rawAdviceStyle?: null | string;
  riskEmotion?: null | string;
  summary?: null | string;
  tagScores: Record<string, number>;
  tradingTempo?: null | string;
}

export function getInvestorPsychProfileQuestionnaire() {
  return requestClient.get<InvestorPsychProfileQuestionnaire>(
    '/ai/investor-psych-profile/questionnaire',
    { responseReturn: 'body' },
  );
}

export function getInvestorPsychProfile() {
  return requestClient.get<InvestorPsychProfile>('/ai/investor-psych-profile', {
    responseReturn: 'body',
  });
}

export function submitInvestorPsychProfile(
  payload: InvestorPsychProfileSubmitPayload,
) {
  return requestClient.post<InvestorPsychProfile>(
    '/ai/investor-psych-profile',
    payload,
    { responseReturn: 'body' },
  );
}

export function updateInvestorPsychProfile(
  payload: InvestorPsychProfileSubmitPayload,
) {
  return requestClient.put<InvestorPsychProfile>(
    '/ai/investor-psych-profile',
    payload,
    { responseReturn: 'body' },
  );
}
