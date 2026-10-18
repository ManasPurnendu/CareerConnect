import React from 'react';
import { Outlet } from 'react-router-dom';
import { Sidebar } from './Sidebar';
import { Header } from './Header';

export const RecruiterLayout: React.FC = () => {
  return (
    <div className="min-h-screen flex bg-warm-bg text-slate-text">
      <Sidebar role="RECRUITER" />
      <div className="flex-1 flex flex-col min-w-0">
        <Header />
        <main className="flex-1 p-4 sm:p-6 lg:p-8 max-w-[1240px] w-full mx-auto">
          <Outlet />
        </main>
      </div>
    </div>
  );
};
