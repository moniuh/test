import { Link } from 'react-router-dom';
import type { MoodWarning } from './types';

/** Wspierający komunikat z dziennika samopoczucia (#76) — bez porad medycznych. */
export function MoodWarningAlert({ warning }: { warning: MoodWarning }) {
  return (
    <div className="alert alert-warning" role="status">
      <div>
        <p><strong>Zadbaj o siebie.</strong> {warning.reasons.join(' ')}</p>
        <p>{warning.message}</p>
        <p>
          Cel tygodniowy zmienisz w <Link to="/profil">profilu</Link> (sugestia na teraz: {warning.suggestedGoal}).
        </p>
        <p style={{ marginBottom: 0 }}>{warning.support}</p>
      </div>
    </div>
  );
}
