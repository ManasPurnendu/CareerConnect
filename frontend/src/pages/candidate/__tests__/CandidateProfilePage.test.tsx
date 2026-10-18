import { describe, it, expect, vi, beforeEach } from 'vitest';
import { render, screen, waitFor, fireEvent } from '@testing-library/react';
import { MemoryRouter } from 'react-router-dom';
import { CandidateProfilePage } from '../CandidateProfilePage';
import { candidateApi } from '../../../api/candidate';
import { AuthContext, type AuthContextType } from '../../../auth/AuthContext';
import type {
  CandidateProfileResponse,
  ProfileReadinessDto,
  CandidateSkillDto,
  EducationDto,
  ProjectDto,
  ExperienceDto,
  ResumeResponse,
  AuthUser,
} from '../../../types/api';

const mockProfile: CandidateProfileResponse = {
  id: 1,
  fullName: 'Alice Candidate',
  headline: 'Full Stack Engineer',
  location: 'San Francisco, CA',
  summary: 'Building great software.',
};

const mockReadiness: ProfileReadinessDto = {
  readyToApply: true,
  profileComplete: true,
  hasEducation: true,
  hasSkills: true,
  hasResume: true,
  missingRequirements: [],
};

const mockSkills: CandidateSkillDto[] = [
  { id: 1, name: 'Java' },
  { id: 2, name: 'React' },
];

const mockEducation: EducationDto[] = [
  {
    id: 10,
    candidateId: 1,
    institution: 'UC Berkeley',
    degree: 'B.S.',
    fieldOfStudy: 'EECS',
    startYear: 2018,
    endYear: 2022,
    sortOrder: 1,
  },
];

const mockProjects: ProjectDto[] = [
  {
    id: 20,
    candidateId: 1,
    title: 'CareerConnect Portal',
    description: 'A modern job board app.',
    sortOrder: 1,
  },
];

const mockExperience: ExperienceDto[] = [
  {
    id: 30,
    candidateId: 1,
    companyName: 'Tech Innovations',
    roleTitle: 'Software Engineer',
    startDate: '2022-06-01',
    isCurrent: true,
    sortOrder: 1,
  },
];

const mockResume: ResumeResponse = {
  id: 100,
  originalFilename: 'alice_resume.pdf',
  contentType: 'application/pdf',
  sizeBytes: 154200,
  isCurrent: true,
  createdAt: '2026-10-18T10:00:00Z',
};

const renderCandidateProfilePage = () => {
  const mockUser: AuthUser = {
    id: 1,
    email: 'alice@example.com',
    role: 'CANDIDATE',
    fullName: 'Alice Candidate',
    isActive: true,
  };

  const mockContext: AuthContextType = {
    user: mockUser,
    token: 'fake-jwt',
    isAuthenticated: true,
    isLoading: false,
    login: vi.fn(),
    register: vi.fn(),
    logout: vi.fn(),
    setUser: vi.fn(),
    refreshCurrentUser: vi.fn(),
  };

  return render(
    <AuthContext.Provider value={mockContext}>
      <MemoryRouter>
        <CandidateProfilePage />
      </MemoryRouter>
    </AuthContext.Provider>
  );
};

describe('CandidateProfilePage', () => {
  beforeEach(() => {
    vi.restoreAllMocks();
    vi.spyOn(candidateApi, 'getProfile').mockResolvedValue(mockProfile);
    vi.spyOn(candidateApi, 'getReadiness').mockResolvedValue(mockReadiness);
    vi.spyOn(candidateApi, 'getSkills').mockResolvedValue(mockSkills);
    vi.spyOn(candidateApi, 'getEducation').mockResolvedValue(mockEducation);
    vi.spyOn(candidateApi, 'getProjects').mockResolvedValue(mockProjects);
    vi.spyOn(candidateApi, 'getExperience').mockResolvedValue(mockExperience);
    vi.spyOn(candidateApi, 'getCurrentResume').mockResolvedValue(mockResume);
  });

  it('renders profile data, readiness indicator, and resume card', async () => {
    renderCandidateProfilePage();

    await waitFor(() => {
      expect(screen.getAllByText('Alice Candidate').length).toBeGreaterThan(0);
      expect(screen.getByText(/Full Stack Engineer/i)).toBeInTheDocument();
      expect(screen.getByText(/Profile Application Ready/i)).toBeInTheDocument();
      expect(screen.getByText('alice_resume.pdf')).toBeInTheDocument();
      expect(screen.getByText('UC Berkeley')).toBeInTheDocument();
      expect(screen.getByText('CareerConnect Portal')).toBeInTheDocument();
      expect(screen.getByText('Tech Innovations')).toBeInTheDocument();
    });
  });

  it('allows adding and removing skills', async () => {
    vi.spyOn(candidateApi, 'assignSkill').mockResolvedValueOnce({ id: 3, name: 'Docker' });
    vi.spyOn(candidateApi, 'removeSkill').mockResolvedValueOnce(undefined);

    renderCandidateProfilePage();

    await waitFor(() => {
      expect(screen.getByText('Java')).toBeInTheDocument();
      expect(screen.getByText('React')).toBeInTheDocument();
    });

    // Add a skill
    const skillInput = screen.getByPlaceholderText(/e\.g\. Java, React, SQL/i);
    fireEvent.change(skillInput, { target: { value: 'Docker' } });

    const addButton = screen.getByRole('button', { name: /add skill/i });
    fireEvent.click(addButton);

    await waitFor(() => {
      expect(candidateApi.assignSkill).toHaveBeenCalledWith({ name: 'Docker' });
      expect(screen.getByText('Docker')).toBeInTheDocument();
    });

    // Remove a skill
    const removeJavaBtn = screen.getByLabelText('Remove Java');
    fireEvent.click(removeJavaBtn);

    await waitFor(() => {
      expect(candidateApi.removeSkill).toHaveBeenCalledWith(1);
      expect(screen.queryByText('Java')).not.toBeInTheDocument();
    });
  });

  it('opens and closes the Education modal', async () => {
    renderCandidateProfilePage();

    await waitFor(() => {
      expect(screen.getByText('UC Berkeley')).toBeInTheDocument();
    });

    const addEduBtn = screen.getByRole('button', { name: /add education/i });
    fireEvent.click(addEduBtn);

    expect(screen.getByRole('heading', { name: /add education/i })).toBeInTheDocument();

    const cancelBtn = screen.getByRole('button', { name: /cancel/i });
    fireEvent.click(cancelBtn);

    await waitFor(() => {
      expect(screen.queryByRole('heading', { name: /add education/i })).not.toBeInTheDocument();
    });
  });
});
